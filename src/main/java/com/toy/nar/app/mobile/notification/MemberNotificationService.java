package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.MemberNotificationListResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationGroup;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberNotificationService {

	private final MemberNotificationRepository notificationRepository;
	private final MemberRepository memberRepository;

	/**
	 * 푸시 발송 성공 시 알림 피드 1행을 기록한다. 피드 기록 실패가 푸시 흐름을 깨면 안 되므로
	 * 호출부(푸시 서비스)에서 예외를 흡수한다.
	 */
	@Transactional
	public void record(
			Long memberId,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data) {
		if (memberId == null || type == null || title == null) {
			return;
		}
		Member member = memberRepository.getReferenceById(memberId);
		notificationRepository.save(new MemberNotification(member, type, title, body, data));
	}

	/**
	 * 여러 구독자에게 같은 알림을 한 번에 기록한다.
	 *
	 * <p>다중 VALUES INSERT 로 직접 넣는다({@code insertAll}). {@code saveAll} 은 이 엔티티에서
	 * 배치가 안 된다 — id 가 {@code GenerationType.IDENTITY} 라 Hibernate 가 INSERT 배치를
	 * 비활성화하고, 그러면 왕복이 구독자 수만큼 난다({@code hibernate.jdbc.batch_size} 무관).
	 * 프로덕션은 앱(EC2 서울)과 DB(OCI 춘천)가 떨어져 있어 왕복당 10ms 대이고, 실측
	 * 2026-08-11 Zeus 구독 1,440명 적재가 약 20초였다. 팬아웃이 발송 전에 피드를 남기므로
	 * 이 시간이 그대로 푸시 지연이 된다.</p>
	 */
	@Transactional
	public void recordAll(
			Collection<Long> memberIds,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data) {
		notificationRepository.insertAll(memberIds, type, title, body, data);
	}

	/**
	 * type 은 한 종류만, group 은 묶음(커뮤니티 4종)을 거른다. 둘 다 오면 더 좁은
	 * type 을 따른다. group 이 있으면 unreadCount 도 그 묶음 기준이다 — 앱 알림함이
	 * 커뮤니티 전용이라, 경기 알림 미읽음이 커뮤니티 벨 배지에 새면 안 된다.
	 */
	public MemberNotificationListResponse getNotifications(
			Long memberId,
			MemberNotificationType type,
			MemberNotificationGroup group,
			int page,
			int size) {
		requireLogin(memberId);
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		PageRequest pageable = PageRequest.of(safePage, safeSize);
		Page<MemberNotification> notifications;
		if (type != null) {
			notifications = notificationRepository.findByMember_IdAndTypeOrderByCreatedAtDesc(memberId, type, pageable);
		} else if (group != null) {
			notifications = notificationRepository
					.findByMember_IdAndTypeInOrderByCreatedAtDesc(memberId, group.types(), pageable);
		} else {
			notifications = notificationRepository.findByMember_IdOrderByCreatedAtDesc(memberId, pageable);
		}
		long unreadCount = type == null && group != null
				? notificationRepository.countByMember_IdAndTypeInAndReadAtIsNull(memberId, group.types())
				: notificationRepository.countByMember_IdAndReadAtIsNull(memberId);

		return new MemberNotificationListResponse(
				notifications.getContent().stream()
						.map(MemberNotificationListResponse.Item::from)
						.toList(),
				unreadCount,
				notifications.getNumber(),
				notifications.getSize(),
				notifications.getTotalElements(),
				notifications.getTotalPages());
	}

	/** group 이 있으면 그 묶음만 읽음 처리한다 — 커뮤니티 알림함의 '모두 읽음'이 경기 알림을 건드리면 안 된다. */
	@Transactional
	public int markAllRead(Long memberId, MemberNotificationGroup group) {
		requireLogin(memberId);
		if (group != null) {
			return notificationRepository.markAllReadByMemberAndTypes(memberId, group.types(), LocalDateTime.now());
		}
		return notificationRepository.markAllReadByMember(memberId, LocalDateTime.now());
	}

	@Transactional
	public void markRead(Long memberId, Long notificationId) {
		requireLogin(memberId);
		MemberNotification notification = notificationRepository.findByIdAndMember_Id(notificationId, memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
		notification.markRead();
	}

	/** 단건 삭제. 본인 알림이 아니거나 없으면 404. */
	@Transactional
	public void delete(Long memberId, Long notificationId) {
		requireLogin(memberId);
		if (notificationRepository.deleteByIdAndMember_Id(notificationId, memberId) == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");
		}
	}

	/** 회원의 알림을 모두 삭제하고 삭제 건수를 반환한다. */
	@Transactional
	public int deleteAll(Long memberId) {
		requireLogin(memberId);
		return notificationRepository.deleteAllByMember(memberId);
	}

	/**
	 * 탈퇴한 회원의 토큰도 만료(최대 30분)까지는 유효하다. 예전엔 회원 존재를 확인하지 않아
	 * 탈퇴 후에도 이 API 만 200 + 빈 목록을 돌려줘서(다른 me/** 는 404) 앱이 로그인 상태로 오인했다.
	 * 회원이 없으면 인증 주체가 사라진 것이므로 401 로 응답해 재로그인 흐름을 타게 한다.
	 */
	private void requireLogin(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		if (!memberRepository.existsById(memberId)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "탈퇴한 회원입니다. 다시 로그인해 주세요.");
		}
	}
}
