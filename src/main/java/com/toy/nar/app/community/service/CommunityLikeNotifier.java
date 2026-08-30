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
 * 좋아요 알림 — 원글 작성자에게 알림함 기록 + FCM 푸시(댓글 알림과 같은 구조,
 * AFTER_COMMIT). <b>(글 × 누른 사람) 최초 1회만</b> — 좋아요↔취소 반복은
 * community_like_notification 기록이 막는다(인스타 방식). 묶음("외 N명")은
 * 규모가 생기면 붙인다 — 지금은 좋아요 절대량이 적어 개별이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityLikeNotifier {

	private final MemberDeviceRepository deviceRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final MemberNotificationService notificationService;
	private final MobilePushGateway pushGateway;
	private final QuietAwarePushSender quietAwarePushSender;

	// REQUIRES_NEW 가 없으면 AFTER_COMMIT 리스너의 INSERT 가 죽은 트랜잭션에 합류해
	// 조용히 증발한다 — 댓글 알림 실사고(#505)와 같은 함정. dedupe INSERT 와
	// 알림함 INSERT 가 같은 트랜잭션이라, 발송 직전에 죽어도 기록만 남는 정도로 무해하다.
	@org.springframework.transaction.annotation.Transactional(
			propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	@TransactionalEventListener
	public void onPostLiked(CommunityPostLikedEvent event) {
		try {
			Long target = event.postAuthorId();
			if (target == null || target == event.actorId()) {
				return; // 탈퇴한 작성자(SET NULL)거나 자기 글에 자기가 누른 경우
			}
			if (interactionRepository.findBlockedMemberIds(target).contains(event.actorId())) {
				return; // 차단한 사람의 좋아요는 알림으로 나타나면 안 된다
			}
			if (interactionRepository.isNotificationMuted(event.postId(), target)) {
				return; // 이 글 벨을 꺼둔 수신자
			}
			if (!interactionRepository.markLikeNotified(event.postId(), event.actorId())) {
				return; // 이미 한 번 알렸던 (글 × 사람) — 재좋아요 도배 방지
			}
			Map<String, String> data = Map.of(
					"type", MemberNotificationType.COMMUNITY_LIKE.name(),
					"postId", String.valueOf(event.postId()));

			notificationService.record(target, MemberNotificationType.COMMUNITY_LIKE,
					event.actorNickname() + "님이 내 글을 좋아해요", event.postTitle(), data);

			if (!pushGateway.isAvailable()) {
				return;
			}
			List<String> tokens = deviceRepository.findByMember_IdAndActiveTrue(target).stream()
					.map(MemberDevice::getFcmToken)
					.toList();
			if (tokens.isEmpty()) {
				return;
			}
			quietAwarePushSender.send(Map.of(target, tokens),
					new MobilePushMessage(event.actorNickname() + "님이 좋아요를 눌렀습니다",
							event.postTitle(), data));
		} catch (Exception e) {
			log.warn("[community] 좋아요 알림 발송 실패 postId={}", event.postId(), e);
		}
	}
}
