package com.toy.nar.app.player;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.participant.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerProfileSyncScheduler {

	private final PlayerService playerService;
	private final SchedulerAlertService schedulerAlertService;

	// LCK 선수 프로필(본명/생년월일/포지션/솔랭 계정) 동기화: 매일 새벽 5시 30분(KST)
	// 새벽 클러스터(04:00~04:30) 직후 한적한 시간대. cron = "초 분 시 일 월 요일"
	@Scheduled(cron = "${player.profile-sync.cron:0 30 5 * * *}", zone = "Asia/Seoul")
	public void syncPlayerProfiles() {
		log.info("Starting scheduled LCK player profile sync...");
		long startTime = System.currentTimeMillis();
		try {
			PlayerProfileSyncResult result = playerService.syncLckPlayerProfiles();
			long elapsed = System.currentTimeMillis() - startTime;
			schedulerAlertService.recordSuccess(
					"PLAYER_PROFILE_SYNC",
					"LCK 선수 프로필 동기화 (성공 " + result.getSuccessCount() + "/" + result.getTotalCount()
							+ ", 실패 " + result.getFailedPlayers() + ")",
					elapsed);
		} catch (Exception e) {
			log.error("Scheduled player profile sync failed", e);
			schedulerAlertService.recordFailure(
					"PLAYER_PROFILE_SYNC",
					"LCK 선수 프로필 동기화",
					e,
					"TrackingThePros 크롤링 동기화 중 오류");
		}
		log.info("Scheduled LCK player profile sync completed.");
	}
}
