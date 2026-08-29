package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberNotificationRetentionServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 4, 40);

	private final MemberNotificationRepository notificationRepository = mock(MemberNotificationRepository.class);
	private final SchedulerAlertService schedulerAlertService = mock(SchedulerAlertService.class);

	/** liveEvent 7일, soloRank 7일, set 30일, community 30일, 청크 5000, 청크 상한 3. */
	private MemberNotificationRetentionService service() {
		return new MemberNotificationRetentionService(
				notificationRepository, schedulerAlertService, 7, 7, 30, 30, 5000, 3);
	}

	@Test
	void 타입별로_서로_다른_컷오프를_적용한다() {
		when(notificationRepository.deleteOlderThanByType(any(), any(), anyInt())).thenReturn(0);

		service().purge(NOW);

		// LIVE_EVENT·PLAYER_SOLO_RANK_STARTED 는 7일, SET_* 는 30일 전이 컷오프다.
		verify(notificationRepository).deleteOlderThanByType(
				eq("LIVE_EVENT"), eq(NOW.minusDays(7)), eq(5000));
		verify(notificationRepository).deleteOlderThanByType(
				eq("PLAYER_SOLO_RANK_STARTED"), eq(NOW.minusDays(7)), eq(5000));
		verify(notificationRepository).deleteOlderThanByType(
				eq("SET_START"), eq(NOW.minusDays(30)), eq(5000));
		verify(notificationRepository).deleteOlderThanByType(
				eq("SET_END"), eq(NOW.minusDays(30)), eq(5000));
	}

	@Test
	void 청크가_가득_차면_같은_타입을_이어서_지운다() {
		// LIVE_EVENT 만 2청크 분량(5000 + 1200), 나머지는 지울 게 없다.
		when(notificationRepository.deleteOlderThanByType(any(), any(), anyInt())).thenReturn(0);
		when(notificationRepository.deleteOlderThanByType(eq("LIVE_EVENT"), any(), anyInt()))
				.thenReturn(5000, 1200);

		int deleted = service().purge(NOW);

		assertThat(deleted).isEqualTo(6200);
		verify(notificationRepository, times(2))
				.deleteOlderThanByType(eq("LIVE_EVENT"), any(), anyInt());
	}

	@Test
	void 청크_상한을_넘기면_남기고_멈춘다() {
		// 계속 가득 찬 청크가 반환돼도 상한(3회)에서 멈춰야 새벽 내내 도는 일이 없다.
		when(notificationRepository.deleteOlderThanByType(any(), any(), anyInt())).thenReturn(0);
		when(notificationRepository.deleteOlderThanByType(eq("LIVE_EVENT"), any(), anyInt()))
				.thenReturn(5000);

		int deleted = service().purge(NOW);

		assertThat(deleted).isEqualTo(15000);
		verify(notificationRepository, times(3))
				.deleteOlderThanByType(eq("LIVE_EVENT"), any(), anyInt());
	}

	@Test
	void 모든_알림_타입에_보존_기간이_설정돼_있다() {
		// 새 타입을 enum 에 추가하고 보존 설정을 빠뜨리면 그 타입만 영구 누적된다.
		when(notificationRepository.deleteOlderThanByType(any(), any(), anyInt())).thenReturn(0);

		service().purge(NOW);

		for (MemberNotificationType type : MemberNotificationType.values()) {
			verify(notificationRepository).deleteOlderThanByType(eq(type.name()), any(), anyInt());
		}
	}

	private static <T> T any() {
		return org.mockito.ArgumentMatchers.any();
	}
}
