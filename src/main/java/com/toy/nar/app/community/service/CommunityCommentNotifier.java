package com.toy.nar.app.community.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.app.mobile.push.MobilePushGateway;
import com.toy.nar.app.mobile.push.MobilePushMessage;
import com.toy.nar.app.mobile.push.QuietAwarePushSender;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 댓글 알림 — 원글 작성자(COMMUNITY_COMMENT)와 답글 대상(COMMUNITY_REPLY)에게
 * 알림함 기록 + FCM 푸시. AFTER_COMMIT 이라 댓글 트랜잭션이 락을 쥔 채 FCM 을
 * 기다리지 않고, 롤백된 댓글로 알림이 나가지 않는다(신고 알림과 같은 구조).
 *
 * <p>알림함 기록은 푸시 성공 여부와 무관하게 남긴다 — 기기 없는(웹만 쓰는) 회원도
 * 알림함에서는 봐야 한다. 발송 실패는 삼킨다 — 알림이 댓글 작성을 깨면 안 된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityCommentNotifier {

	private final MemberDeviceRepository deviceRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final MemberNotificationService notificationService;
	private final MobilePushGateway pushGateway;
	private final QuietAwarePushSender quietAwarePushSender;

	@TransactionalEventListener
	public void onCommentCreated(CommunityCommentCreatedEvent event) {
		try {
			// 답글 대상이 원글 작성자와 같은 사람이면 REPLY 하나만 — 두 번 울리면 도배다.
			notify(event.replyTargetId(), MemberNotificationType.COMMUNITY_REPLY,
					event.authorNickname() + "님이 회원님의 댓글에 답글을 남겼어요", event);
			Long postAuthor = event.postAuthorId();
			if (postAuthor != null && !postAuthor.equals(event.replyTargetId())) {
				notify(postAuthor, MemberNotificationType.COMMUNITY_COMMENT,
						event.authorNickname() + "님이 회원님의 글에 댓글을 남겼어요", event);
			}
		} catch (Exception e) {
			log.warn("[community] 댓글 알림 발송 실패 commentId={}", event.commentId(), e);
		}
	}

	private void notify(Long targetMemberId, MemberNotificationType type, String title,
			CommunityCommentCreatedEvent event) {
		if (targetMemberId == null || targetMemberId == event.authorId()) {
			return; // 탈퇴한 작성자(SET NULL)거나 자기 글·댓글에 단 경우
		}
		// 수신자가 작성자를 차단했으면 알림도 안 간다 — 목록에서 숨긴 사람이
		// 알림으로 다시 나타나면 차단이 뚫린 것처럼 보인다.
		if (interactionRepository.findBlockedMemberIds(targetMemberId).contains(event.authorId())) {
			return;
		}
		Map<String, String> data = Map.of(
				"type", type.name(),
				"postId", String.valueOf(event.postId()),
				"commentId", String.valueOf(event.commentId()));

		notificationService.record(targetMemberId, type, title, event.bodyPreview(), data);

		if (!pushGateway.isAvailable()) {
			return;
		}
		List<String> tokens = deviceRepository.findByMember_IdAndActiveTrue(targetMemberId).stream()
				.map(MemberDevice::getFcmToken)
				.toList();
		if (tokens.isEmpty()) {
			return;
		}
		quietAwarePushSender.send(Map.of(targetMemberId, tokens),
				new MobilePushMessage(title, event.bodyPreview(), data));
	}
}
