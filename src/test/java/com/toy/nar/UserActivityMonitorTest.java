package com.toy.nar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.UserActivityMonitor;
import com.toy.nar.app.monitor.UserActivityService;

@ExtendWith(MockitoExtension.class)
class UserActivityMonitorTest {

	@InjectMocks
	private UserActivityMonitor userActivityMonitor;

	@Mock // 🔹 가짜(Mock) 객체로 만들 서비스 1
	private UserActivityService userActivityService;

	@Mock // 🔹 가짜(Mock) 객체로 만들 서비스 2
	private NotificationService notificationService;

	@Test
	@DisplayName("사용자 수가 10명을 처음 넘으면 알림을 1회 보낸다")
	void notifyWhenCrossingFirstThreshold() {
		// Given (상황): UserActivityService가 12명의 사용자를 반환하도록 설정
		when(userActivityService.getActiveUsersCount()).thenReturn(12L);

		// When (행동): 모니터링 로직을 실행
		userActivityMonitor.checkUserCountAndNotify();

		// Then (결과): NotificationService가 '10'이라는 값으로 정확히 1번 호출되었는지 검증
		verify(notificationService, times(1)).sendUserCountNotification(10);
	}

	@Test
	@DisplayName("사용자 수가 10명은 넘었지만 다음 임계점(20명)은 넘지 않으면 추가 알림을 보내지 않는다")
	void doNotNotifyWhenBelowNextThreshold() {
		// Given (상황):
		// 1. 처음 12명으로 알림이 한 번 갔던 상황을 시뮬레이션
		when(userActivityService.getActiveUsersCount()).thenReturn(12L);
		userActivityMonitor.checkUserCountAndNotify();

		// 2. 이후 사용자 수가 19명으로 증가
		when(userActivityService.getActiveUsersCount()).thenReturn(19L);

		// When (행동): 모니터링 로직을 다시 실행
		userActivityMonitor.checkUserCountAndNotify();

		// Then (결과): NotificationService의 sendUserCountNotification 메서드는
		// 이전에 호출된 1번 외에 추가로 호출되지 않았는지 검증
		verify(notificationService, times(1)).sendUserCountNotification(10);
		verifyNoMoreInteractions(notificationService); // 그 외 다른 상호작용이 없었음을 확인
	}

	@Test
	@DisplayName("사용자 수가 20명을 넘으면 20명 돌파 알림을 새로 보낸다")
	void notifyWhenCrossingSecondThreshold() {
		// Given: 12명일 때 10명 돌파 알림이 갔고
		when(userActivityService.getActiveUsersCount()).thenReturn(12L);
		userActivityMonitor.checkUserCountAndNotify();

		// 21명으로 증가한 상황
		when(userActivityService.getActiveUsersCount()).thenReturn(21L);

		// When: 모니터링 로직 실행
		userActivityMonitor.checkUserCountAndNotify();

		// Then: '20'이라는 값으로 알림 메서드가 호출되었는지 검증
		verify(notificationService).sendUserCountNotification(20);
	}

	@Test
	@DisplayName("사용자 수가 임계점(10명) 미만이면 알림을 보내지 않는다")
	void doNotNotifyWhenBelowFirstThreshold() {
		// Given: 사용자 수가 9명
		when(userActivityService.getActiveUsersCount()).thenReturn(9L);

		// When: 모니터링 실행
		userActivityMonitor.checkUserCountAndNotify();

		// Then: NotificationService가 어떤 값으로도 전혀 호출되지 않았는지 검증
		verify(notificationService, never()).sendUserCountNotification(anyLong());
	}
}
