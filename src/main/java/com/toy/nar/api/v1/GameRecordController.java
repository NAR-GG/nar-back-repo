package com.toy.nar.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.toy.nar.app.record.GameRecordService;
import com.toy.nar.app.record.dto.GameRecordDto;

@Tag(name = "4. 게임 기록 (상세)", description = "특정 게임의 세부 전적, 밴픽, 인게임 지표 조회")
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameRecordController {

	private final GameRecordService gameRecordService;

	@Operation(summary = "게임 상세 데이터 조회", description = "게임 ID(gameId)를 통해 해당 매치의 밴픽, 선수별 스탯, 골드 차이 등 상세 정보를 반환합니다.")
	@GetMapping("/{gameId}/record")
	public ResponseEntity<GameRecordDto> getGameRecord(@PathVariable Long gameId) {
		GameRecordDto gameRecord = gameRecordService.getGameRecord(gameId);
		return ResponseEntity.ok(gameRecord);
	}
}
