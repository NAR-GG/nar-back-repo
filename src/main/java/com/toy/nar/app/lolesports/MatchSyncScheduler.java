package com.toy.nar.app.lolesports;

import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.schedule.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSyncScheduler {

	private final LeagueMatchService leagueMatchService;
	private final SchedulerAlertService schedulerAlertService;
	private final CacheEvictionService cacheEvictionService;
	@Value("${lolesports.sync.active-leagues:}")
	private String configuredActiveLeagues;

	// 기본 6시간 주기 (기존 30분 -> 비용 절감)
	@Scheduled(cron = "${lolesports.sync.cron:0 0 */6 * * *}")
	public void syncAllLeagues() {
		List<String> targetLeagues = resolveTargetLeagues();
		log.info("Starting scheduled match sync for leagues={}", targetLeagues);
		for (String league : targetLeagues) {
			long startTime = System.currentTimeMillis();
			try {
				leagueMatchService.syncMatchesWithoutTeamMetadata(league);
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
		cacheEvictionService.evictScheduleCaches();
		log.info("Scheduled match sync completed.");
	}

	// 팀 메타데이터 동기화는 1일 1회 배치로 분리
	@Scheduled(cron = "${lolesports.sync.team-metadata-cron:0 15 4 * * *}")
	public void syncTeamMetadataDaily() {
		List<String> targetLeagues = resolveTargetLeagues();
		long startTime = System.currentTimeMillis();
		try {
			int updated = leagueMatchService.syncTeamMetadataForLeagues(targetLeagues);
			long elapsed = System.currentTimeMillis() - startTime;
			log.info("Daily team metadata sync completed. updated={} leagues={}", updated, targetLeagues);
			schedulerAlertService.recordSuccess("TEAM_METADATA_SYNC", "팀 메타데이터 동기화", elapsed);
		} catch (Exception e) {
			log.error("Daily team metadata sync failed. leagues={}", targetLeagues, e);
			schedulerAlertService.recordFailure(
					"TEAM_METADATA_SYNC",
					"팀 메타데이터 동기화",
					e,
					"leagues=" + targetLeagues);
		}
	}

	private List<String> resolveTargetLeagues() {
		if (configuredActiveLeagues == null || configuredActiveLeagues.isBlank()) {
			return LeagueConstants.TARGET_LEAGUES;
		}
		List<String> leagues = Arrays.stream(configuredActiveLeagues.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(String::toUpperCase)
				.distinct()
				.toList();
		return leagues.isEmpty() ? LeagueConstants.TARGET_LEAGUES : leagues;
	}
}
