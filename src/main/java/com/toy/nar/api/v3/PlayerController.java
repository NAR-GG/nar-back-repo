package com.toy.nar.api.v3;

import com.toy.nar.app.participant.dto.PlayerImageSyncResult;
import com.toy.nar.app.participant.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1.3 선수 관리", description = "선수 정보 관리 API")
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

	private final PlayerService playerService;

	@Operation(summary = "선수 이미지 URL 수동 업데이트", description = "특정 선수의 이미지 URL을 수동으로 업데이트합니다.")
	@PostMapping("/{playerId}/image")
	public ResponseEntity<String> updatePlayerImage(
		@PathVariable Long playerId,
		@RequestParam String imageUrl) {
		
		playerService.updatePlayerImage(playerId, imageUrl);
		return ResponseEntity.ok("Player image updated successfully.");
	}

	@Operation(summary = "LCK 선수 이미지 URL 일괄 동기화", description = "LCK 선수들의 이미지 URL을 확인하고 유효한 경우 업데이트하며, 실패한 선수 목록을 반환합니다.")
	@PostMapping("/sync-images")
	public ResponseEntity<PlayerImageSyncResult> syncLckPlayerImages() {
		PlayerImageSyncResult result = playerService.syncLckPlayerImages();
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "전체 선수 이미지 URL 초기화", description = "모든 선수의 이미지 URL을 null로 초기화합니다.")
	@DeleteMapping("/images")
	public ResponseEntity<String> resetAllPlayerImages() {
		int count = playerService.resetAllPlayerImages();
		return ResponseEntity.ok("Reset images for " + count + " players.");
	}
}
