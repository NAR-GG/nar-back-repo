package com.toy.nar.app.lolesports;

import com.toy.nar.app.monitor.SchedulerAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSyncScheduler {

	private final LeagueMatchService leagueMatchService;
	private final SchedulerAlertService schedulerAlertService;

	// 1시간마다 실행 (cron = "0 0 * * * *")
	// 테스트용으로 10분마다 실행 (cron = "0 0/10 * * * *")
	// 현재: 30분 주기로 실행하여 6시간 이내의 변동사항을 반영
	@Scheduled(cron = "0 0/30 * * * *")
	public void syncAllLeagues() {
		log.info("Starting scheduled match sync...");
		for (String league : LeagueConstants.TARGET_LEAGUES) {
			long startTime = System.currentTimeMillis();
			try {
				leagueMatchService.syncMatches(league);
				long elapsed = System.currentTimeMillis() - startTime;
				schedulerAlertService.recordSuccess("MATCH_SYNC", "리그 경기 동기화", elapsed);
			} catch (Exception e) {
				log.error("Failed to sync league: {}", league, e);
				schedulerAlertService.recordFailure(
						"MATCH_SYNC",
						"리그 경기 동기화",
						e,
						"league=" + league);
			}

			try {
				// API 부하 방지를 위해 리그 간 5초 딜레이
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				schedulerAlertService.recordFailure(
						"MATCH_SYNC",
						"리그 경기 동기화",
						e,
						"리그 간 대기 중 인터럽트 발생");
				break;
			}
		}
		log.info("Scheduled match sync completed.");
	}
}
