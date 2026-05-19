package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveSimulationResponse;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSimulationService {

	private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final int MAX_TICKS = 240;

	private final LiveStatsClient liveStatsClient;
	private final LiveFrameProcessor liveFrameProcessor;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final LiveGameMetadataService liveGameMetadataService;

	public LiveSimulationResponse simulate(String gameId, String startingTime, int ticks, int stepSeconds) {
		LocalDateTime firstStartingTimeUtc = parseStartingTime(startingTime);
		int safeTicks = Math.max(1, Math.min(ticks, MAX_TICKS));
		int safeStepSeconds = Math.max(1, stepSeconds);
		ActiveLiveGame activeGame = liveGameMetadataService.enrich(resolveActiveGame(gameId));

		int processedFrames = 0;
		int emptyResponses = 0;
		int failures = 0;
		LocalDateTime firstFrameTimestampUtc = null;
		LocalDateTime lastFrameTimestampUtc = null;

		for (int index = 0; index < safeTicks; index++) {
			LocalDateTime tickStartUtc = firstStartingTimeUtc.plusSeconds((long) index * safeStepSeconds);
			try {
				JsonNode windowResponse = liveStatsClient.getWindow(gameId, formatStartingTime(tickStartUtc));
				JsonNode detailsResponse = liveStatsClient.getDetails(gameId, formatStartingTime(tickStartUtc));
				if (!hasFrames(windowResponse) || !hasFrames(detailsResponse)) {
					emptyResponses++;
					continue;
				}
				Optional<LiveGameState> state = liveFrameProcessor.process(activeGame, windowResponse, detailsResponse);
				if (state.isEmpty()) {
					emptyResponses++;
					continue;
				}
				processedFrames++;
				LocalDateTime frameTimestampUtc = state.get().frameTimestampUtc();
				if (firstFrameTimestampUtc == null || frameTimestampUtc.isBefore(firstFrameTimestampUtc)) {
					firstFrameTimestampUtc = frameTimestampUtc;
				}
				if (lastFrameTimestampUtc == null || frameTimestampUtc.isAfter(lastFrameTimestampUtc)) {
					lastFrameTimestampUtc = frameTimestampUtc;
				}
			} catch (Exception e) {
				failures++;
				log.warn("Live simulation failed for gameId={} startingTime={}: {}", gameId, tickStartUtc, e.getMessage());
			}
		}

		return new LiveSimulationResponse(
				gameId,
				firstStartingTimeUtc,
				safeTicks,
				safeStepSeconds,
				processedFrames,
				emptyResponses,
				failures,
				firstFrameTimestampUtc,
				lastFrameTimestampUtc);
	}

	private ActiveLiveGame resolveActiveGame(String gameId) {
		Optional<LeagueMatchGame> matchGame = leagueMatchGameRepository.findWithMatchByGameId(gameId);
		if (matchGame.isEmpty()) {
			return new ActiveLiveGame(gameId, null, null, null, null, LocalDateTime.now(ZoneOffset.UTC), 0);
		}
		LeagueMatch match = matchGame.get().getLeagueMatch();
		return new ActiveLiveGame(
				gameId,
				match.getId(),
				match.getLeagueName(),
				match.getBlueTeamName(),
				match.getRedTeamName(),
				LocalDateTime.now(ZoneOffset.UTC),
				0);
	}

	private LocalDateTime parseStartingTime(String startingTime) {
		try {
			return LocalDateTime.ofInstant(Instant.parse(startingTime), ZoneOffset.UTC);
		} catch (Exception e) {
			return LocalDateTime.parse(startingTime, DateTimeFormatter.ISO_DATE_TIME);
		}
	}

	private String formatStartingTime(LocalDateTime startingTimeUtc) {
		return startingTimeUtc.atOffset(ZoneOffset.UTC).format(START_TIME_FORMATTER);
	}

	private boolean hasFrames(JsonNode response) {
		return response != null && response.path("frames").isArray() && !response.path("frames").isEmpty();
	}
}
