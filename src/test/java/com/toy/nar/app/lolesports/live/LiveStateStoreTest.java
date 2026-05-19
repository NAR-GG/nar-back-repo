package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveStateStoreTest {

	@Test
	void doesNotOverwriteLatestStateWithOlderFrame() {
		LiveStateStore store = new LiveStateStore();
		LiveGameState newer = stateAt(LocalDateTime.of(2026, 5, 16, 12, 0, 10));
		LiveGameState older = stateAt(LocalDateTime.of(2026, 5, 16, 12, 0, 5));

		store.putLatestState(newer);
		store.putLatestState(older);

		assertThat(store.getLatestState("game-1"))
				.hasValueSatisfying(state -> assertThat(state.frameTimestampUtc()).isEqualTo(newer.frameTimestampUtc()));
	}

	private LiveGameState stateAt(LocalDateTime frameTimestampUtc) {
		return new LiveGameState(
				"game-1",
				"match-1",
				"LCK",
				"Blue",
				"Red",
				frameTimestampUtc.withSecond(0),
				frameTimestampUtc,
				List.of(),
				List.of());
	}
}
