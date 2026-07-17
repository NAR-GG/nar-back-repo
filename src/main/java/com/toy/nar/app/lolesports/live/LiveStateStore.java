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
	/**
	 * 프레임 gameState=finished 로 종료가 확정된 gameId. 게임은 stale 확정(3분)까지 store 에
	 * 남으므로, 그 잔상 동안 세트 상태를 LIVE 로 표시하지 않기 위한 마킹이다.
	 */
	private final java.util.Set<String> finishedGameIds = ConcurrentHashMap.newKeySet();

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
		finishedGameIds.remove(gameId);
	}

	public void markFinished(String gameId) {
		finishedGameIds.add(gameId);
	}

	public boolean isFinished(String gameId) {
		return finishedGameIds.contains(gameId);
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
