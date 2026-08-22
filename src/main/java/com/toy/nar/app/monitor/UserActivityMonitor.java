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
	 *
	 * <p><b>지금은 실질적으로 동작하지 않는다.</b> {@code @Scheduled} 라 스케줄러 파드에서만
	 * 도는데, 집계를 채우는 UserActivityFilter 는 웹 파드에 있다. 두 파드의
	 * UserActivityService 는 서로 다른 JVM 이라 여기서 보는 값은 항상 0 이다.
	 * 되살리려면 웹 파드에서 돌게 하거나(스케줄러 플래그와 분리) 집계를 공유
	 * 저장소로 옮겨야 한다. 지금은 Grafana 대시보드(nar.active.users)가 이 역할을 한다.
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
