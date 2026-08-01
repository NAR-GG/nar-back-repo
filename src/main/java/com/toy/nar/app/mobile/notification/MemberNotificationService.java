package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.MemberNotificationListResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberNotification;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
	 * <p>fan-out 에서 구독자마다 {@link #record} 를 부르면 INSERT 왕복이 구독자 수만큼 난다.
	 * saveAll 로 넘기면 {@code hibernate.jdbc.batch_size}(50) 단위로 묶이고,
	 * prod 는 {@code rewriteBatchedStatements=true} 라 다중 VALUES 로 다시 쓰인다.</p>
	 */
	@Transactional
	public void recordAll(
			Collection<Long> memberIds,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data) {
		if (memberIds == null || memberIds.isEmpty() || type == null || title == null) {
			return;
		}
		List<MemberNotification> notifications = memberIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.map(memberId -> new MemberNotification(
						memberRepository.getReferenceById(memberId), type, title, body, data))
				.toList();
		notificationRepository.saveAll(notifications);
	}

	public MemberNotificationListResponse getNotifications(
			Long memberId,
			MemberNotificationType type,
			int page,
			int size) {
		requireLogin(memberId);
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		PageRequest pageable = PageRequest.of(safePage, safeSize);
		Page<MemberNotification> notifications = type == null
				? notificationRepository.findByMember_IdOrderByCreatedAtDesc(memberId, pageable)
				: notificationRepository.findByMember_IdAndTypeOrderByCreatedAtDesc(memberId, type, pageable);

		return new MemberNotificationListResponse(
				notifications.getContent().stream()
						.map(MemberNotificationListResponse.Item::from)
						.toList(),
				notificationRepository.countByMember_IdAndReadAtIsNull(memberId),
				notifications.getNumber(),
				notifications.getSize(),
				notifications.getTotalElements(),
				notifications.getTotalPages());
	}

	@Transactional
	public int markAllRead(Long memberId) {
		requireLogin(memberId);
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
