package com.toy.nar.app.riot;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerRiotAccountSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerRiotAccountSyncScheduler {

	private final PlayerRiotAccountSyncService playerRiotAccountSyncService;
	private final RiotApiProperties riotApiProperties;
	private final SchedulerAlertService schedulerAlertService;

	// LCK 선수 주 계정 Riot 식별자(puuid/gameName) 동기화: 매일 새벽 6시(KST).
	// 프로필 크롤러(05:30)가 gameAccounts 를 갱신한 직후 실행해, 선수가 솔랭 닉을
	// 바꿔도 추적 테이블(player_riot_account)이 자동으로 따라가게 한다.
	// (기존에는 수동 API 호출뿐이라 닉 변경 시 stale 계정으로 남는 문제가 있었다)
	@Scheduled(cron = "${riot.account-sync.cron:0 0 6 * * *}", zone = "Asia/Seoul")
	public void syncPrimaryRiotAccounts() {
		if (!riotApiProperties.isEnabled()) {
			log.info("Riot API disabled. Skipping scheduled riot account sync.");
			return;
		}
		log.info("Starting scheduled riot primary account sync...");
		long startTime = System.currentTimeMillis();
		try {
			PlayerRiotAccountSyncResult result = playerRiotAccountSyncService.syncPrimaryAccounts();
			long elapsed = System.currentTimeMillis() - startTime;
			schedulerAlertService.recordSuccess(
					"PLAYER_RIOT_ACCOUNT_SYNC",
					"LCK 선수 Riot 계정 동기화 (동기화 " + result.syncedCount() + "/" + result.totalPlayers()
							+ ", 스킵 " + result.skippedPlayers() + ", 실패 " + result.failedPlayers() + ")",
					elapsed);
		} catch (Exception e) {
			log.error("Scheduled riot account sync failed", e);
			schedulerAlertService.recordFailure(
					"PLAYER_RIOT_ACCOUNT_SYNC",
					"LCK 선수 Riot 계정 동기화",
					e,
					"Riot by-riot-id 계정 동기화 중 오류");
		}
		log.info("Scheduled riot primary account sync completed.");
	}
}
