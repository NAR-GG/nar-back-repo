package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LiveFrameProcessor {

	private final LiveStateAggregator liveStateAggregator;
	private final LiveStateStore liveStateStore;
	private final LivePersistenceQueue livePersistenceQueue;

	public Optional<LiveGameState> process(ActiveLiveGame activeGame, JsonNode windowResponse, JsonNode detailsResponse) {
		if (activeGame == null || windowResponse == null || detailsResponse == null) {
			return Optional.empty();
		}

		LiveGameState state = liveStateAggregator.aggregate(activeGame, windowResponse, detailsResponse);
		if (state == null) {
			return Optional.empty();
		}
		if (isStaleOrDuplicate(state)) {
			return Optional.empty();
		}

		liveStateStore.putLatestState(state);
		livePersistenceQueue.enqueueSnapshot(state);
		livePersistenceQueue.enqueueObjectEvents(activeGame, windowResponse);
		liveStateStore.getActiveGames().put(
				activeGame.gameId(),
				activeGame.withLastSeenAt(LocalDateTime.now(ZoneOffset.UTC)).clearFailures());
		return Optional.of(state);
	}

	private boolean isStaleOrDuplicate(LiveGameState state) {
		return liveStateStore.getLatestState(state.gameId())
				.filter(existing -> existing.frameTimestampUtc() != null && state.frameTimestampUtc() != null)
				.map(existing -> !state.frameTimestampUtc().isAfter(existing.frameTimestampUtc()))
				.orElse(false);
	}
}
