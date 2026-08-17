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
	 * 종료가 확정된 gameId. 게임은 stale 확정(3분)까지 store 에 남으므로, 그 잔상 동안 세트 상태를
	 * LIVE 로 표시하지 않기 위한 마킹이다.
	 *
	 * <p>추적이 끝난 뒤에도 지우지 않는다 — 종료는 게임 단위의 사실이고, 지우면 종료된 세트가
	 * 다시 LIVE 로 부활한다(2026-08-17 KeSPA T1 vs DNS 2세트: 20:33 종료 확정 → 3분 뒤 추적 제거로
	 * 마킹까지 사라짐 → livestats 가 종료 후에도 옛 프레임을 계속 돌려줘 20:42 재편입 → 3세트가
	 * 시작된 뒤에도 2·3세트가 동시에 LIVE).</p>
	 *
	 * <p>ponytail: TTL 캐시로 바꾸지 않는다. 하루 수십 개 gameId 문자열이고 재기동마다 비므로
	 * 만료 관리가 이득이 없다.</p>
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

	/** 추적만 끝낸다. 종료 마킹은 남긴다 — {@link #finishedGameIds} 주석 참고. */
	public void removeGame(String gameId) {
		activeGames.remove(gameId);
		latestStates.remove(gameId);
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
