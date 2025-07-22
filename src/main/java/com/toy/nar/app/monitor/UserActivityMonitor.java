package com.toy.nar.app.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.toy.nar.app.data.source.NotificationService;

@Service
@RequiredArgsConstructor
public class UserActivityMonitor {

	private final UserActivityService userActivityService;
	private final NotificationService notificationService;

	private int lastNotifiedThreshold = 0;

	/**
	 * 1분마다 실행되어 실시간 접속자 수를 확인하고, 필요 시 알림을 보냅니다.
	 */
	@Scheduled(fixedRate = 60 * 1000)
	public void checkUserCountAndNotify() {
		long currentUserCount = userActivityService.getActiveUsersCount();

		// 현재 사용자 수에 해당하는 임계점 계산 (17명 -> 10, 23명 -> 20)
		int currentThreshold = (int) (currentUserCount / 10) * 10;

		if (currentThreshold > lastNotifiedThreshold) {
			notificationService.sendUserCountNotification(currentThreshold);
			this.lastNotifiedThreshold = currentThreshold;
		}
	}
}
