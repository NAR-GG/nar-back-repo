package com.toy.nar.app.monitor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityService {

	private static final long ACTIVE_DURATION_MS = 5 * 60 * 1000;

	private final Map<String, Long> userActivity = new ConcurrentHashMap<>();
	private final Clock clock;

	/**
	 * 특정 사용자의 활동을 기록합니다.
	 * @param userIdentifier 사용자 식별자 (예: IP 주소, 세션 ID)
	 */
	public void recordUserActivity(String userIdentifier) {
		userActivity.put(userIdentifier, clock.millis());
	}

	/**
	 * 현재 활성 사용자 수를 반환합니다.
	 * @return 5분 내에 활동한 고유 사용자 수
	 */
	public long getActiveUsersCount() {
		long now = clock.millis();
		return userActivity.values().stream()
			.filter(lastActivityTime -> (now - lastActivityTime) < ACTIVE_DURATION_MS)
			.count();
	}

	/**
	 * 1분마다 실행되어 5분이 지난 오래된 사용자 정보를 삭제합니다.
	 */
	// @Scheduled(fixedRate = 60 * 1000)
	public void cleanupOldUsers() {
		long now = clock.millis();
		userActivity.entrySet()
			.removeIf(entry -> (now - entry.getValue()) > ACTIVE_DURATION_MS);
	}
}