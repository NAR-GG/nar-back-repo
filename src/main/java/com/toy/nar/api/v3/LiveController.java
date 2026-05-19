package com.toy.nar.api.v3;

import com.toy.nar.app.lolesports.live.LiveBackfillService;
import com.toy.nar.app.lolesports.live.LivePersistenceQueue;
import com.toy.nar.app.lolesports.live.LiveSimulationService;
import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.live.dto.LiveBackfillResponse;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveGameSummaryResponse;
import com.toy.nar.app.lolesports.live.dto.LiveSimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "7. Live API", description = "라이브 경기 상태 조회 API")
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

	private final LiveStateStore liveStateStore;
	private final LiveStateQueryService liveStateQueryService;
	private final LiveBackfillService liveBackfillService;
	private final LiveSimulationService liveSimulationService;
	private final LivePersistenceQueue livePersistenceQueue;

	@Operation(summary = "현재 라이브 게임 목록 조회", description = "백엔드에서 폴링 중인 라이브 게임의 최신 상태 목록을 조회합니다.")
	@GetMapping("/games")
	public ResponseEntity<List<LiveGameSummaryResponse>> getLiveGames() {
		return ResponseEntity.ok(liveStateStore.getLiveGameSummaries());
	}

	@Operation(summary = "라이브 게임 최신 상태 조회", description = "특정 gameId의 최신 라이브 상태를 조회합니다.")
	@GetMapping("/games/{gameId}")
	public ResponseEntity<LiveGameState> getLiveGame(@PathVariable String gameId) {
		return liveStateQueryService.getLatestState(gameId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@Operation(summary = "분 단위 라이브 스냅샷 조회", description = "DB에 저장된 최근 60분 스냅샷을 최신순으로 조회합니다.")
	@GetMapping("/games/{gameId}/minutes")
	public ResponseEntity<List<LiveGameState>> getRecentMinuteSnapshots(@PathVariable String gameId) {
		return ResponseEntity.ok(liveStateQueryService.getRecentMinuteSnapshots(gameId));
	}

	@Operation(summary = "라이브 게임 백필 실행", description = "actual_game_start_time 우선으로 시작 시각을 잡고, 없으면 분 단위로 startingTime을 늘려가며 백필합니다.")
	@PostMapping("/games/{gameId}/backfill")
	public ResponseEntity<LiveBackfillResponse> backfillGame(@PathVariable String gameId) {
		return ResponseEntity.ok(liveBackfillService.backfillByMinute(gameId));
	}

	@Operation(summary = "과거 live feed 시뮬레이션", description = "과거 startingTime을 기준으로 window/details API를 재생해 현재 live processor를 검증합니다.")
	@PostMapping("/games/{gameId}/simulate")
	public ResponseEntity<LiveSimulationResponse> simulateGame(
			@PathVariable String gameId,
			@RequestParam String startingTime,
			@RequestParam(defaultValue = "12") int ticks,
			@RequestParam(defaultValue = "5") int stepSeconds) {
		return ResponseEntity.ok(liveSimulationService.simulate(gameId, startingTime, ticks, stepSeconds));
	}

	@Operation(summary = "라이브 저장 큐 상태 조회", description = "snapshot/object event 비동기 저장 큐 상태를 조회합니다.")
	@GetMapping("/queue")
	public ResponseEntity<LivePersistenceQueue.LiveQueueStats> getQueueStats() {
		return ResponseEntity.ok(livePersistenceQueue.stats());
	}
}
