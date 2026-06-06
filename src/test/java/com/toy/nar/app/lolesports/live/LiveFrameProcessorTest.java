package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveFrameProcessorTest {

	private final LiveStateAggregator liveStateAggregator = mock(LiveStateAggregator.class);
	private final LiveStateStore liveStateStore = new LiveStateStore();
	private final LivePersistenceQueue livePersistenceQueue = mock(LivePersistenceQueue.class);
	private final LiveFrameProcessor processor = new LiveFrameProcessor(
			liveStateAggregator,
			liveStateStore,
			livePersistenceQueue);

	@Test
	void nullFeedResponseIsSkipped() {
		Optional<LiveGameState> result = processor.process(activeGame(), mock(JsonNode.class), null);

		assertThat(result).isEmpty();
		verify(liveStateAggregator, never()).aggregate(any(), any(), any());
		verify(livePersistenceQueue, never()).enqueueSnapshot(any());
		verify(livePersistenceQueue, never()).enqueueObjectEvents(any(), any());
	}

	@Test
	void duplicateFrameIsNotPersistedAgain() {
		ActiveLiveGame activeGame = activeGame();
		JsonNode window = mock(JsonNode.class);
		JsonNode details = mock(JsonNode.class);
		LiveGameState state = stateAt(LocalDateTime.of(2026, 5, 29, 12, 0, 10));
		liveStateStore.putLatestState(state);
		when(liveStateAggregator.aggregate(activeGame, window, details)).thenReturn(state);

		Optional<LiveGameState> result = processor.process(activeGame, window, details);

		assertThat(result).isEmpty();
		verify(livePersistenceQueue, never()).enqueueSnapshot(any());
		verify(livePersistenceQueue, never()).enqueueObjectEvents(any(), any());
	}

	@Test
	void newerFrameUpdatesStateAndQueuesPersistence() {
		ActiveLiveGame activeGame = activeGame();
		JsonNode window = mock(JsonNode.class);
		JsonNode details = mock(JsonNode.class);
		LiveGameState previous = stateAt(LocalDateTime.of(2026, 5, 29, 12, 0, 10));
		LiveGameState newer = stateAt(LocalDateTime.of(2026, 5, 29, 12, 0, 20));
		liveStateStore.putLatestState(previous);
		when(liveStateAggregator.aggregate(activeGame, window, details)).thenReturn(newer);

		Optional<LiveGameState> result = processor.process(activeGame, window, details);

		assertThat(result).contains(newer);
		assertThat(liveStateStore.getLatestState("game-1")).contains(newer);
		verify(livePersistenceQueue).enqueueSnapshot(newer);
		verify(livePersistenceQueue).enqueueObjectEvents(activeGame, window);
	}

	private ActiveLiveGame activeGame() {
		return new ActiveLiveGame(
				"game-1",
				"match-1",
				"LCK",
				"DK",
				"BRO",
				LocalDateTime.now(ZoneOffset.UTC),
				0);
	}

	private LiveGameState stateAt(LocalDateTime frameTimestampUtc) {
		return new LiveGameState(
				"game-1",
				"match-1",
				"LCK",
				"DK",
				"BRO",
				frameTimestampUtc.withSecond(0),
				frameTimestampUtc,
				List.of(),
				List.of());
	}
}
