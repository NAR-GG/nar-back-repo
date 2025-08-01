package com.toy.nar.api.v1;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.toy.nar.app.record.GameRecordService;
import com.toy.nar.app.record.dto.GameRecordDto;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameRecordController {

	private final GameRecordService gameRecordService;

	@GetMapping("/{gameId}/record")
	public ResponseEntity<GameRecordDto> getGameRecord(@PathVariable Long gameId) {
		GameRecordDto gameRecord = gameRecordService.getGameRecord(gameId);
		return ResponseEntity.ok(gameRecord);
	}
}
