package com.toy.nar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.toy.nar.app.monitor.UserActivityService;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

	@InjectMocks
	private UserActivityService userActivityService;

	@Mock
	private Clock clock;

	// 테스트에서 사용할 기준 시간
	private final long MOCK_START_TIME = Instant.parse("2025-07-22T12:00:00Z").toEpochMilli();

	@Test
	@DisplayName("고유한 IP 주소 3개는 활성 사용자 3명으로 집계되어야 한다")
	void shouldCountThreeForThreeUniqueIps() {
		// Given: 현재 시간을 설정하고, 3개의 다른 IP로 활동을 기록
		when(clock.millis()).thenReturn(MOCK_START_TIME);
		userActivityService.recordUserActivity("1.1.1.1");
		userActivityService.recordUserActivity("2.2.2.2");
		userActivityService.recordUserActivity("3.3.3.3");

		// When: 활성 사용자 수를 조회
		long activeUsers = userActivityService.getActiveUsersCount();

		// Then: 결과는 3명이어야 함
		assertEquals(3, activeUsers);
	}

	@Test
	@DisplayName("동일한 IP 주소로 여러 번 활동해도 활성 사용자 1명으로 집계되어야 한다")
	void shouldCountOneForMultipleActivitiesFromSameIp() {
		// Given: 현재 시간을 설정하고, 동일한 IP로 여러번 활동 기록
		when(clock.millis()).thenReturn(MOCK_START_TIME);
		userActivityService.recordUserActivity("1.1.1.1");
		userActivityService.recordUserActivity("1.1.1.1");

		// When: 활성 사용자 수를 조회
		long activeUsers = userActivityService.getActiveUsersCount();

		// Then: 결과는 1명이어야 함
		assertEquals(1, activeUsers);
	}

	@Test
	@DisplayName("5분이 지난 사용자는 활성 사용자 수에서 제외되어야 한다")
	void shouldNotCountExpiredUser() {
		// Given: 1번 사용자는 10분 전에 활동했고, 2번 사용자는 방금 활동함
		long tenMinutesAgo = MOCK_START_TIME - (10 * 60 * 1000);
		when(clock.millis()).thenReturn(tenMinutesAgo);
		userActivityService.recordUserActivity("1.1.1.1"); // 10분 전 사용자

		when(clock.millis()).thenReturn(MOCK_START_TIME);
		userActivityService.recordUserActivity("2.2.2.2"); // 현재 사용자

		// When: 현재 시간 기준으로 활성 사용자 수를 조회
		long activeUsers = userActivityService.getActiveUsersCount();

		// Then: 5분 내에 활동한 2번 사용자만 집계되어야 함
		assertEquals(1, activeUsers);
	}

	@Test
	@DisplayName("cleanup 스케줄러는 5분이 지난 오래된 사용자 정보를 삭제해야 한다")
	void cleanupTaskShouldRemoveOldUsers() {
		// Given: 1번 사용자는 10분 전, 2번 사용자는 현재 활동
		when(clock.millis()).thenReturn(MOCK_START_TIME - (10 * 60 * 1000));
		userActivityService.recordUserActivity("1.1.1.1");

		when(clock.millis()).thenReturn(MOCK_START_TIME);
		userActivityService.recordUserActivity("2.2.2.2");

		// When: cleanup 메서드를 수동으로 실행
		userActivityService.cleanupOldUsers();

		// Then: 5분 내에 활동한 사용자(2번)만 남아있어야 하므로, 전체 사용자 수는 1명이어야 함
		// getActiveUsersCount를 다시 호출하여 확인
		assertEquals(1, userActivityService.getActiveUsersCount());
	}
}
