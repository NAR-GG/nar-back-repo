package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.MemberNotificationListResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberNotification;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberNotificationServiceTest {

	@Mock
	private MemberNotificationRepository notificationRepository;

	@Mock
	private MemberRepository memberRepository;

	private MemberNotificationService service() {
		return new MemberNotificationService(notificationRepository, memberRepository);
	}

	@Test
	void recordSavesFeedRowWithMemberReference() {
		Member member = member(7L);
		when(memberRepository.getReferenceById(7L)).thenReturn(member);

		service().record(7L, MemberNotificationType.SET_END, "T1 세트 종료",
				"T1 vs GEN · 1세트 종료", Map.of("matchId", "m1"));

		ArgumentCaptor<MemberNotification> captor = ArgumentCaptor.forClass(MemberNotification.class);
		verify(notificationRepository).save(captor.capture());
		MemberNotification saved = captor.getValue();
		assertThat(saved.getType()).isEqualTo(MemberNotificationType.SET_END);
		assertThat(saved.getTitle()).isEqualTo("T1 세트 종료");
		assertThat(saved.getData()).containsEntry("matchId", "m1");
		assertThat(saved.getMember()).isSameAs(member);
	}

	@Test
	void recordIgnoresMissingMemberOrType() {
		service().record(null, MemberNotificationType.SET_END, "t", "b", Map.of());
		service().record(7L, null, "t", "b", Map.of());
		verify(notificationRepository, never()).save(any());
	}

	@Test
	void getNotificationsWithoutTypeReturnsAllWithUnreadCount() {
		MemberNotification read = notification(1L, MemberNotificationType.SET_START, true);
		MemberNotification unread = notification(2L, MemberNotificationType.LIVE_EVENT, false);
		when(notificationRepository.findByMember_IdOrderByCreatedAtDesc(eq(7L), any()))
				.thenReturn(new PageImpl<>(List.of(unread, read), PageRequest.of(0, 20), 2));
		when(notificationRepository.countByMember_IdAndReadAtIsNull(7L)).thenReturn(1L);

		MemberNotificationListResponse response = service().getNotifications(7L, null, 0, 20);

		assertThat(response.notifications()).hasSize(2);
		assertThat(response.unreadCount()).isEqualTo(1L);
		assertThat(response.totalElements()).isEqualTo(2L);
		assertThat(response.notifications().get(0).read()).isFalse();
		verify(notificationRepository, never()).findByMember_IdAndTypeOrderByCreatedAtDesc(any(), any(), any());
	}

	@Test
	void getNotificationsWithTypeFiltersByType() {
		when(notificationRepository.findByMember_IdAndTypeOrderByCreatedAtDesc(
				eq(7L), eq(MemberNotificationType.SET_END), any()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		when(notificationRepository.countByMember_IdAndReadAtIsNull(7L)).thenReturn(0L);

		service().getNotifications(7L, MemberNotificationType.SET_END, 0, 20);

		verify(notificationRepository).findByMember_IdAndTypeOrderByCreatedAtDesc(
				eq(7L), eq(MemberNotificationType.SET_END), any());
		verify(notificationRepository, never()).findByMember_IdOrderByCreatedAtDesc(any(), any());
	}

	@Test
	void markReadLoadsScopedToMemberAndMarks() {
		MemberNotification notification = notification(9L, MemberNotificationType.SET_START, false);
		when(notificationRepository.findByIdAndMember_Id(9L, 7L)).thenReturn(java.util.Optional.of(notification));

		service().markRead(7L, 9L);

		assertThat(notification.isRead()).isTrue();
	}

	@Test
	void getNotificationsRequiresLogin() {
		assertThatThrownBy(() -> service().getNotifications(null, null, 0, 20))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void deleteRemovesWhenOwnedByMember() {
		when(notificationRepository.deleteByIdAndMember_Id(9L, 7L)).thenReturn(1);

		service().delete(7L, 9L);

		verify(notificationRepository).deleteByIdAndMember_Id(9L, 7L);
	}

	@Test
	void deleteThrowsNotFoundWhenNotOwnedOrMissing() {
		when(notificationRepository.deleteByIdAndMember_Id(9L, 7L)).thenReturn(0);

		assertThatThrownBy(() -> service().delete(7L, 9L))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void deleteAllReturnsDeletedCount() {
		when(notificationRepository.deleteAllByMember(7L)).thenReturn(3);

		assertThat(service().deleteAll(7L)).isEqualTo(3);
	}

	@Test
	void deleteRequiresLogin() {
		assertThatThrownBy(() -> service().delete(null, 9L))
				.isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> service().deleteAll(null))
				.isInstanceOf(ResponseStatusException.class);
		verify(notificationRepository, never()).deleteByIdAndMember_Id(any(), any());
		verify(notificationRepository, never()).deleteAllByMember(any());
	}

	private Member member(Long id) {
		Member member = Member.builder().name("nick").tag("0000").email("a@b.c").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private MemberNotification notification(Long id, MemberNotificationType type, boolean read) {
		MemberNotification n = new MemberNotification(member(7L), type, "title", "body", Map.of());
		ReflectionTestUtils.setField(n, "id", id);
		if (read) {
			ReflectionTestUtils.setField(n, "readAt", LocalDateTime.now());
		}
		return n;
	}
}
