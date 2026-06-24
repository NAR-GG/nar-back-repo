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

	private void requireLogin(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
	}
}
