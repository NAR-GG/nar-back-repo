package com.toy.nar.app.community.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.app.mobile.push.MobilePushGateway;
import com.toy.nar.app.mobile.push.QuietAwarePushSender;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;

/** 좋아요 알림 판정 — 자기 글·차단·뮤트·재좋아요(dedupe)를 거른다. */
class CommunityLikeNotifierTest {

	private final MemberDeviceRepository devices = mock(MemberDeviceRepository.class);
	private final CommunityInteractionRepository interactions = mock(CommunityInteractionRepository.class);
	private final MemberNotificationService notifications = mock(MemberNotificationService.class);
	private final MobilePushGateway gateway = mock(MobilePushGateway.class);
	private final QuietAwarePushSender quietSender = mock(QuietAwarePushSender.class);

	private final CommunityLikeNotifier notifier = new CommunityLikeNotifier(
			devices, interactions, notifications, gateway, quietSender);

	private CommunityPostLikedEvent event(Long postAuthorId, long actorId) {
		return new CommunityPostLikedEvent(1L, actorId, "액터#0001", postAuthorId, "글 제목");
	}

	@Test
	void 처음_누른_좋아요는_원글_작성자에게_기록된다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of());
		when(interactions.isNotificationMuted(1L, 2L)).thenReturn(false);
		when(interactions.markLikeNotified(1L, 7L)).thenReturn(true);
		when(devices.findByMember_IdAndActiveTrue(anyLong())).thenReturn(List.of());

		notifier.onPostLiked(event(2L, 7L));

		verify(notifications).record(eq(2L), eq(MemberNotificationType.COMMUNITY_LIKE),
				eq("액터#0001님이 내 글을 좋아해요"), eq("글 제목"), any());
	}

	@Test
	void 재좋아요는_dedupe_기록에_막힌다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of());
		when(interactions.isNotificationMuted(1L, 2L)).thenReturn(false);
		when(interactions.markLikeNotified(1L, 7L)).thenReturn(false);

		notifier.onPostLiked(event(2L, 7L));

		verify(notifications, never()).record(any(), any(), any(), any(), any());
	}

	@Test
	void 자기_글_좋아요와_탈퇴_작성자는_알림이_없다() {
		notifier.onPostLiked(event(7L, 7L));
		notifier.onPostLiked(event(null, 7L));
		verify(notifications, never()).record(any(), any(), any(), any(), any());
		verify(interactions, never()).markLikeNotified(anyLong(), anyLong());
	}

	@Test
	void 차단_뮤트면_dedupe_기록도_남기지_않는다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of(7L));
		notifier.onPostLiked(event(2L, 7L));

		when(interactions.findBlockedMemberIds(3L)).thenReturn(List.of());
		when(interactions.isNotificationMuted(1L, 3L)).thenReturn(true);
		notifier.onPostLiked(new CommunityPostLikedEvent(1L, 7L, "액터#0001", 3L, "글 제목"));

		verify(notifications, never()).record(any(), any(), any(), any(), any());
		verify(interactions, never()).markLikeNotified(anyLong(), anyLong());
	}
}
