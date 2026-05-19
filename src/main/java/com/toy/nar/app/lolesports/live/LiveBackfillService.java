package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.live.dto.LiveBackfillResponse;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameMappingRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveBackfillService {

	private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private final LiveStatsClient liveStatsClient;
	private final LiveStateAggregator liveStateAggregator;
	private final LiveMinuteSnapshotWriter snapshotWriter;
	private final LiveObjectEventRecorder liveObjectEventRecorder;
	private final LiveStateStore liveStateStore;
	private final LiveGameMappingRepository liveGameMappingRepository;
	private final LiveGameMinuteSnapshotRepository snapshotRepository;
	private final GameRepository gameRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;

	@Value("${lolesports.live.backfill.probe-max-minutes:360}")
	private int probeMaxMinutes;

	@Value("${lolesports.live.backfill.collect-max-minutes:120}")
	private int collectMaxMinutes;

	@Value("${lolesports.live.backfill.stop-empty-streak:8}")
	private int stopEmptyStreak;

	@Value("${lolesports.live.backfill.max-failures:5}")
	private int maxFailures;

	public LiveBackfillResponse backfillByMinute(String gameId) {
		LiveContext context = resolveContext(gameId);
		StartCandidate baseCandidate = resolveBaseStartingTime(gameId, context);

		ProbeResult probeResult = probeFirstAvailableStartingTime(gameId, baseCandidate.baseStartUtc());
		if (probeResult == null) {
			return new LiveBackfillResponse(
					gameId,
					baseCandidate.source(),
					baseCandidate.baseStartUtc(),
					null,
					probeMaxMinutes + 1,
					0,
					0,
					probeMaxMinutes + 1,
					0,
					"NO_DATA_IN_PROBE_WINDOW",
					null,
					null);
		}

		int snapshotsWritten = 0;
		int emptyResponses = 0;
		int failures = 0;
		int emptyStreak = 0;
		int staleFrameStreak = 0;
		int requestedMinutes = 0;
		String stopReason = "MAX_MINUTES_REACHED";
		LocalDateTime firstFrameTimestampUtc = null;
		LocalDateTime lastFrameTimestampUtc = null;

		for (int minuteOffset = 0; minuteOffset < collectMaxMinutes; minuteOffset++) {
			requestedMinutes++;
			LocalDateTime requestStartUtc = toMinuteUtc(probeResult.foundStartingTimeUtc().plusMinutes(minuteOffset));
			try {
				JsonNode windowResponse = minuteOffset == 0
						? probeResult.windowResponse()
						: liveStatsClient.getWindow(gameId, formatStartingTime(requestStartUtc));
				JsonNode detailsResponse = minuteOffset == 0
						? probeResult.detailsResponse()
						: liveStatsClient.getDetails(gameId, formatStartingTime(requestStartUtc));

				if (!hasFrames(windowResponse) || !hasFrames(detailsResponse)) {
					emptyResponses++;
					emptyStreak++;
					if (emptyStreak >= stopEmptyStreak) {
						stopReason = "EMPTY_STREAK_REACHED";
						break;
					}
					continue;
				}

				emptyStreak = 0;
				liveObjectEventRecorder.record(context.toActiveLiveGame(), windowResponse);
				LiveGameState state = liveStateAggregator.aggregate(context.toActiveLiveGame(), windowResponse, detailsResponse);
				if (state == null) {
					continue;
				}

				if (lastFrameTimestampUtc != null && !state.frameTimestampUtc().isAfter(lastFrameTimestampUtc)) {
					staleFrameStreak++;
					if (staleFrameStreak >= stopEmptyStreak) {
						stopReason = "STALE_FRAME_STREAK_REACHED";
						break;
					}
					continue;
				}

				staleFrameStreak = 0;

				snapshotWriter.write(state);
				liveStateStore.putLatestState(state);
				snapshotsWritten++;

				if (firstFrameTimestampUtc == null || state.frameTimestampUtc().isBefore(firstFrameTimestampUtc)) {
					firstFrameTimestampUtc = state.frameTimestampUtc();
				}
				if (lastFrameTimestampUtc == null || state.frameTimestampUtc().isAfter(lastFrameTimestampUtc)) {
					lastFrameTimestampUtc = state.frameTimestampUtc();
				}
			} catch (Exception e) {
				failures++;
				log.warn("Live backfill failed for game {} at {}: {}", gameId, requestStartUtc, e.getMessage());
				if (failures >= maxFailures) {
					stopReason = "MAX_FAILURES_REACHED";
					break;
				}
			}
		}

		return new LiveBackfillResponse(
				gameId,
				baseCandidate.source(),
				baseCandidate.baseStartUtc(),
				probeResult.foundStartingTimeUtc(),
				probeResult.probeAttempts(),
				requestedMinutes,
				snapshotsWritten,
				emptyResponses,
				failures,
				stopReason,
				firstFrameTimestampUtc,
				lastFrameTimestampUtc);
	}

	private ProbeResult probeFirstAvailableStartingTime(String gameId, LocalDateTime baseStartUtc) {
		for (int minuteOffset = 0; minuteOffset <= probeMaxMinutes; minuteOffset++) {
			LocalDateTime candidate = toMinuteUtc(baseStartUtc.plusMinutes(minuteOffset));
			String startingTime = formatStartingTime(candidate);
			try {
				JsonNode windowResponse = liveStatsClient.getWindow(gameId, startingTime);
				JsonNode detailsResponse = liveStatsClient.getDetails(gameId, startingTime);
				if (hasFrames(windowResponse) && hasFrames(detailsResponse)) {
					return new ProbeResult(candidate, minuteOffset + 1, windowResponse, detailsResponse);
				}
			} catch (Exception e) {
				log.debug("Live backfill probe failed for game {} at {}: {}", gameId, candidate, e.getMessage());
			}
		}
		return null;
	}

	private StartCandidate resolveBaseStartingTime(String gameId, LiveContext context) {
		Optional<LiveGameMapping> mapping = liveGameMappingRepository.findByLiveGameId(gameId);

		if (mapping.isPresent()) {
			LiveGameMapping liveGameMapping = mapping.get();
			if (liveGameMapping.getInternalGameId() != null) {
				Optional<Game> game = gameRepository.findById(liveGameMapping.getInternalGameId());
				if (game.isPresent()) {
					return new StartCandidate(
							toMinuteUtc(game.get().getActualGameStartTime()),
							"INTERNAL_GAME_ACTUAL_START_TIME");
				}
			}
		}

		Optional<LeagueMatch> matchByContext = context.matchId() == null || context.matchId().isBlank()
				? Optional.empty()
				: leagueMatchRepository.findById(context.matchId());
		Optional<LeagueMatch> matchByGameId = findLeagueMatchByGameId(gameId);
		Optional<LeagueMatch> effectiveMatch = matchByContext.isPresent() ? matchByContext : matchByGameId;
		if (effectiveMatch.isPresent() && effectiveMatch.get().getMatchDate() != null) {
			return new StartCandidate(
					toMinuteUtc(effectiveMatch.get().getMatchDate().minusHours(2)),
					"MATCH_DATE_MINUS_2H");
		}

		Optional<LiveGameMinuteSnapshot> firstSnapshot = snapshotRepository.findTopByGameIdOrderByMinuteBucketUtcAsc(gameId);
		if (firstSnapshot.isPresent()) {
			return new StartCandidate(
					toMinuteUtc(firstSnapshot.get().getMinuteBucketUtc()),
					"FIRST_MINUTE_SNAPSHOT");
		}

		return new StartCandidate(
				toMinuteUtc(LocalDateTime.now(ZoneOffset.UTC).minusHours(3)),
				"NOW_MINUS_3H_FALLBACK");
	}

	private LiveContext resolveContext(String gameId) {
		Optional<LiveGameMapping> mapping = liveGameMappingRepository.findByLiveGameId(gameId);
		if (mapping.isPresent()) {
			LiveGameMapping liveGameMapping = mapping.get();
			return new LiveContext(
					gameId,
					liveGameMapping.getLiveMatchId(),
					liveGameMapping.getLiveLeagueName(),
					liveGameMapping.getLiveBlueTeamName(),
					liveGameMapping.getLiveRedTeamName());
		}

		Optional<LiveGameMinuteSnapshot> latest = snapshotRepository.findTopByGameIdOrderByFrameTimestampUtcDesc(gameId);
		if (latest.isPresent()) {
			LiveGameMinuteSnapshot snapshot = latest.get();
			return new LiveContext(
					gameId,
					snapshot.getMatchId(),
					snapshot.getLeagueName(),
					snapshot.getBlueTeamName(),
					snapshot.getRedTeamName());
		}

		Optional<LeagueMatch> leagueMatch = findLeagueMatchByGameId(gameId);
		if (leagueMatch.isPresent()) {
			LeagueMatch match = leagueMatch.get();
			return new LiveContext(
					gameId,
					match.getId(),
					match.getLeagueName(),
					match.getBlueTeamName(),
					match.getRedTeamName());
		}

		return new LiveContext(gameId, null, null, null, null);
	}

	private Optional<LeagueMatch> findLeagueMatchByGameId(String gameId) {
		return leagueMatchGameRepository.findWithMatchByGameId(gameId)
				.map(LeagueMatchGame::getLeagueMatch);
	}

	private boolean hasFrames(JsonNode response) {
		return response != null && response.path("frames").isArray() && !response.path("frames").isEmpty();
	}

	private LocalDateTime toMinuteUtc(LocalDateTime value) {
		return value.withSecond(0).withNano(0);
	}

	private String formatStartingTime(LocalDateTime utcTime) {
		return toMinuteUtc(utcTime).format(START_TIME_FORMATTER);
	}

	private record StartCandidate(LocalDateTime baseStartUtc, String source) {
	}

	private record ProbeResult(
			LocalDateTime foundStartingTimeUtc,
			int probeAttempts,
			JsonNode windowResponse,
			JsonNode detailsResponse) {
	}

	private record LiveContext(
			String gameId,
			String matchId,
			String leagueName,
			String blueTeamName,
			String redTeamName) {
		ActiveLiveGame toActiveLiveGame() {
			return new ActiveLiveGame(
					gameId,
					matchId,
					leagueName,
					blueTeamName,
					redTeamName,
					LocalDateTime.now(ZoneOffset.UTC),
					0);
		}
	}
}
