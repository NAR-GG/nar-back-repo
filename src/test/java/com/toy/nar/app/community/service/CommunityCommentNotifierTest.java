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

/** 댓글 알림 수신자 판정 — 자기 자신·차단·중복(원글 작성자 == 답글 대상)을 거른다. */
class CommunityCommentNotifierTest {

	private final MemberDeviceRepository devices = mock(MemberDeviceRepository.class);
	private final CommunityInteractionRepository interactions = mock(CommunityInteractionRepository.class);
	private final MemberNotificationService notifications = mock(MemberNotificationService.class);
	private final MobilePushGateway gateway = mock(MobilePushGateway.class);
	private final QuietAwarePushSender quietSender = mock(QuietAwarePushSender.class);

	private final CommunityCommentNotifier notifier = new CommunityCommentNotifier(
			devices, interactions, notifications, gateway, quietSender);

	private CommunityCommentCreatedEvent event(Long postAuthorId, Long replyTargetId, long authorId) {
		return new CommunityCommentCreatedEvent(1L, 10L, authorId, "작성자#0001",
				postAuthorId, replyTargetId, "댓글 내용");
	}

	@Test
	void 원글_작성자에게_COMMENT_알림이_기록된다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of());
		when(devices.findByMember_IdAndActiveTrue(anyLong())).thenReturn(List.of());

		notifier.onCommentCreated(event(2L, null, 7L));

		verify(notifications).record(eq(2L), eq(MemberNotificationType.COMMUNITY_COMMENT),
				any(), any(), any());
	}

	@Test
	void 자기_글에_단_댓글은_알림이_없다() {
		notifier.onCommentCreated(event(7L, null, 7L));
		verify(notifications, never()).record(any(), any(), any(), any(), any());
	}

	@Test
	void 답글_대상이_원글_작성자면_REPLY_하나만_간다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of());
		when(devices.findByMember_IdAndActiveTrue(anyLong())).thenReturn(List.of());

		notifier.onCommentCreated(event(2L, 2L, 7L));

		verify(notifications).record(eq(2L), eq(MemberNotificationType.COMMUNITY_REPLY),
				any(), any(), any());
		verify(notifications, never()).record(any(), eq(MemberNotificationType.COMMUNITY_COMMENT),
				any(), any(), any());
	}

	@Test
	void 글_알림을_꺼둔_수신자에게는_알림이_없다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of());
		when(interactions.isNotificationMuted(1L, 2L)).thenReturn(true);

		notifier.onCommentCreated(event(2L, null, 7L));

		verify(notifications, never()).record(any(), any(), any(), any(), any());
	}

	@Test
	void 수신자가_작성자를_차단했으면_알림이_없다() {
		when(interactions.findBlockedMemberIds(2L)).thenReturn(List.of(7L));

		notifier.onCommentCreated(event(2L, null, 7L));

		verify(notifications, never()).record(any(), any(), any(), any(), any());
	}
}
