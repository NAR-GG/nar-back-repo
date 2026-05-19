package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveGameSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveStateStore {

	private final Map<String, ActiveLiveGame> activeGames = new ConcurrentHashMap<>();
	private final Map<String, LiveGameState> latestStates = new ConcurrentHashMap<>();

	public Map<String, ActiveLiveGame> getActiveGames() {
		return activeGames;
	}

	public void putLatestState(LiveGameState state) {
		latestStates.compute(state.gameId(), (gameId, existing) -> {
			if (existing == null) {
				return state;
			}
			if (existing.frameTimestampUtc() == null) {
				return state;
			}
			if (state.frameTimestampUtc() == null) {
				return existing;
			}
			return state.frameTimestampUtc().isAfter(existing.frameTimestampUtc()) ? state : existing;
		});
	}

	public Optional<LiveGameState> getLatestState(String gameId) {
		return Optional.ofNullable(latestStates.get(gameId));
	}

	public void removeGame(String gameId) {
		activeGames.remove(gameId);
		latestStates.remove(gameId);
	}

	public List<LiveGameSummaryResponse> getLiveGameSummaries() {
		return latestStates.values().stream()
				.sorted(Comparator.comparing(LiveGameState::frameTimestampUtc).reversed())
				.map(state -> new LiveGameSummaryResponse(
						state.gameId(),
						state.matchId(),
						state.leagueName(),
						state.blueTeamName(),
						state.redTeamName(),
						state.frameTimestampUtc()))
				.toList();
	}
}
