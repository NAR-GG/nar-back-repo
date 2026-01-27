package com.toy.nar.app.lolesports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSyncScheduler {

	private final LeagueMatchService leagueMatchService;

	// 1시간마다 실행 (cron = "0 0 * * * *")
	// 테스트용으로 10분마다 실행 (cron = "0 0/10 * * * *")
	// 현재: 30분 주기로 실행하여 6시간 이내의 변동사항을 반영
	@Scheduled(cron = "0 0/30 * * * *")
	public void syncAllLeagues() {
		log.info("Starting scheduled match sync...");
		for (String league : LeagueMatchService.TARGET_LEAGUES) {
			try {
				leagueMatchService.syncMatches(league);
				// API 부하 방지를 위해 리그 간 5초 딜레이
				Thread.sleep(5000);
			} catch (Exception e) {
				log.error("Failed to sync league: {}", league, e);
			}
		}
		log.info("Scheduled match sync completed.");
	}
}
