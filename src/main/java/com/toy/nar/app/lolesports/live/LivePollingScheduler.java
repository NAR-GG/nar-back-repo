package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lolesports.live", name = "enabled", havingValue = "true")
public class LivePollingScheduler {

	private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final long INITIAL_LOOKBACK_SECONDS = 90L;

	private final WorldsService worldsService;
	private final LiveStatsClient liveStatsClient;
	private final LiveObjectEventRecorder liveObjectEventRecorder;
	private final LiveStateStore liveStateStore;
	private final LiveFrameProcessor liveFrameProcessor;
	private final LiveGameMetadataService liveGameMetadataService;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.stale-threshold-ms:180000}")
	private long staleThresholdMs;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.max-consecutive-failures:6}")
	private int maxConsecutiveFailures;

	@Scheduled(fixedDelayString = "${lolesports.live.discovery-interval-ms:60000}")
	public void discoverLiveGames() {
		Map<String, ActiveLiveGame> activeGames = liveStateStore.getActiveGames();
		Set<String> discoveredGameIds = new HashSet<>();
		LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

		for (String league : LeagueConstants.TARGET_LEAGUES) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				for (MatchResultDto match : response.getMatches()) {
					if (!"inProgress".equalsIgnoreCase(match.getState()) || match.getLiveGameIds() == null) {
						continue;
					}

					for (String gameId : match.getLiveGameIds()) {
						if (gameId == null || gameId.isBlank()) {
							continue;
						}
						discoveredGameIds.add(gameId);
						ActiveLiveGame current = activeGames.get(gameId);
						String resolvedLeagueName = (match.getLeagueName() == null || match.getLeagueName().isBlank())
								? league
								: match.getLeagueName();
						ActiveLiveGame next = new ActiveLiveGame(
								gameId,
								match.getMatchId(),
								resolvedLeagueName,
								match.getBlueTeam() != null ? match.getBlueTeam().getName() : null,
								match.getRedTeam() != null ? match.getRedTeam().getName() : null,
								nowUtc,
								current != null ? current.consecutiveFailures() : 0);
						activeGames.put(gameId, next);
						liveGameMetadataService.remember(next);
					}
				}
			} catch (Exception e) {
				log.warn("Live discovery failed for league {}: {}", league, e.getMessage());
			}
		}

		List<String> toRemove = new ArrayList<>();
		for (ActiveLiveGame activeGame : activeGames.values()) {
			boolean notDiscovered = !discoveredGameIds.contains(activeGame.gameId());
			boolean stale = activeGame.lastSeenAtUtc() != null
					&& java.time.Duration.between(activeGame.lastSeenAtUtc(), nowUtc).toMillis() > staleThresholdMs;
			if (notDiscovered && stale) {
				toRemove.add(activeGame.gameId());
			}
		}

		toRemove.forEach(gameId -> {
			liveStateStore.removeGame(gameId);
			liveObjectEventRecorder.evict(gameId);
		});
	}

	@Scheduled(fixedDelayString = "${lolesports.live.poll-interval-ms:5000}")
	public void pollActiveGames() {
		List<ActiveLiveGame> activeGames = new ArrayList<>(liveStateStore.getActiveGames().values());
		if (activeGames.isEmpty()) {
			return;
		}

		for (ActiveLiveGame activeGame : activeGames) {
			try {
				String startingTime = computeStartingTime(activeGame.gameId());
				JsonNode window = liveStatsClient.getWindow(activeGame.gameId(), startingTime);
				JsonNode details = liveStatsClient.getDetails(activeGame.gameId(), startingTime);
				liveFrameProcessor.process(activeGame, window, details);
			} catch (Exception e) {
				ActiveLiveGame failed = activeGame.increaseFailures();
				if (failed.consecutiveFailures() >= maxConsecutiveFailures) {
					log.warn("Removing game {} after {} consecutive polling failures", failed.gameId(), failed.consecutiveFailures());
					liveStateStore.removeGame(failed.gameId());
					liveObjectEventRecorder.evict(failed.gameId());
					continue;
				}
				liveStateStore.getActiveGames().put(failed.gameId(), failed);
				log.warn("Live polling failed for game {}: {}", failed.gameId(), e.getMessage());
			}
		}
	}

	private String computeStartingTime(String gameId) {
		Instant candidate = liveStateStore.getLatestState(gameId)
				.map(state -> state.frameTimestampUtc().toInstant(ZoneOffset.UTC).minusSeconds(10))
				.orElseGet(() -> Instant.now().minusSeconds(INITIAL_LOOKBACK_SECONDS));

		Instant minAllowed = Instant.now().minus(Duration.ofMinutes(5));
		if (candidate.isBefore(minAllowed)) {
			candidate = minAllowed;
		}

		long flooredSeconds = (candidate.getEpochSecond() / 10) * 10;
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(flooredSeconds), ZoneOffset.UTC).format(START_TIME_FORMATTER);
	}
}
