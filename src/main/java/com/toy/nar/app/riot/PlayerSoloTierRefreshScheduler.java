package com.toy.nar.app.riot;

import com.toy.nar.app.monitor.SchedulerAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerSoloTierRefreshScheduler {

	private final PlayerSoloTierRefreshService refreshService;
	private final RiotApiProperties riotApiProperties;
	private final SchedulerAlertService schedulerAlertService;

	// 추적 계정 솔랭 티어: 매시 40분. 계정 ~175개라 시간당 호출 ~175건(prod 키 한도 30,000/10분).
	@Scheduled(cron = "${riot.solo-tier.cron:0 40 * * * *}", zone = "Asia/Seoul")
	public void refresh() {
		if (!riotApiProperties.isEnabled()) {
			return;
		}
		long startTime = System.currentTimeMillis();
		try {
			int failed = refreshService.refreshAll();
			schedulerAlertService.recordSuccess("PLAYER_SOLO_TIER_REFRESH", "선수 솔랭 티어 갱신 (실패 " + failed + ")",
					System.currentTimeMillis() - startTime);
		} catch (Exception e) {
			log.error("Scheduled solo tier refresh failed", e);
			schedulerAlertService.recordFailure("PLAYER_SOLO_TIER_REFRESH", "선수 솔랭 티어 갱신", e,
					"Riot league-v4 조회 중 오류");
		}
	}

	// 판이 끝난 계정만 LP 반영 뒤 바로 갱신. 대상은 DB 에서 도출하므로 인메모리 상태가 없다.
	@Scheduled(fixedDelayString = "${riot.solo-tier.ended-sweep-ms:30000}", initialDelayString = "${riot.solo-tier.ended-initial-delay-ms:60000}")
	public void refreshRecentlyEnded() {
		if (!riotApiProperties.isEnabled()) {
			return;
		}
		try {
			int refreshed = refreshService.refreshRecentlyEnded(LocalDateTime.now());
			if (refreshed > 0) {
				log.info("Solo tier refreshed after game end: {}", refreshed);
			}
		} catch (Exception e) {
			log.warn("Solo tier refresh after game end failed", e);
		}
	}
}
