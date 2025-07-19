package com.toy.nar.common.data.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.common.data.dto.DataSyncResult;
import com.toy.nar.common.data.service.GoogleDriveDataSyncService;
import com.toy.nar.common.data.service.LeagueRepairService;
import com.toy.nar.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/google-drive")
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveController {

	private final GoogleDriveDataSyncService googleDriveDataSyncService;
	private final LeagueRepairService leagueRepairService;
	private final GameRepository gameRepository;
	/**
	 * Google Drive에서 데이터 동기화
	 */
		@PostMapping("/sync")
	public ResponseEntity<Map<String, Object>> syncData() {
		log.info("🚀 Google Drive sync requested via API");

		DataSyncResult result = googleDriveDataSyncService.syncFromGoogleDrive();

		if (result.isSuccess()) {
			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "동기화가 성공적으로 완료되었습니다",
				"summary", result.getSummary(),
				"data", Map.of(
					"totalRowsProcessed", result.totalRowsProcessed(),
					"newGamesAdded", result.newGamesAdded(),
					"skippedGames", result.skippedGames(),
					"invalidGames", result.invalidGames(),
					"failedGames", result.failedGames(),
					"processingTimeMs", result.processingTimeMs(),
					"successRate", result.successRate()
				)
			));
		} else {
			return ResponseEntity.status(500).body(Map.of(
				"success", false,
				"message", "동기화 중 오류가 발생했습니다",
				"error", result.errorMessage(),
				"summary", result.getSummary(),
				"data", Map.of(
					"totalRowsProcessed", result.totalRowsProcessed(),
					"processingTimeMs", result.processingTimeMs(),
					"source", result.source()
				)
			));
		}
	}

	/**
	 * Google Drive 파일 상태 확인
	 */
	@GetMapping("/file-status")
	public ResponseEntity<Map<String, Object>> checkFileStatus() {
		log.info("📋 File status check requested");

		String fileStatus = googleDriveDataSyncService.checkFileStatus();

		return ResponseEntity.ok(Map.of(
			"success", true,
			"message", "파일 상태 확인 완료",
			"fileStatus", fileStatus
		));
	}

	/**
	 * 동기화 서비스 상태 확인 (헬스체크)
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> healthCheck() {
		return ResponseEntity.ok(Map.of(
			"status", "HEALTHY",
			"service", "Google Drive Data Sync Service",
			"timestamp", System.currentTimeMillis(),
			"fileId", "1v6LRphp2kYciU4SXp0PCjEMuev1bDejc"
		));
	}

	@PostMapping("/repair-ewc")
	public ResponseEntity<Map<String, Object>> repairEWC() {
		log.info("🔧 EWC repair requested via API");

		leagueRepairService.repairEWCData();
		return ResponseEntity.ok(Map.of(
			"success", true,
			"message", "EWC 데이터 복구가 성공적으로 완료되었습니다"
		));
	}

}