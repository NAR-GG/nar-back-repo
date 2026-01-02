package com.toy.nar.api.admin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.data.maintenance.GameStatusAnalyzer;
import com.toy.nar.app.data.maintenance.dto.CleanupResult;
import com.toy.nar.app.data.source.DriveTestService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.data.source.dto.DataSyncResult;
import com.toy.nar.app.data.maintenance.DataReconciliationService;
import com.toy.nar.app.data.maintenance.DataVerificationService;
import com.toy.nar.app.data.maintenance.GameCleanupService;
import com.toy.nar.app.data.source.GoogleDriveDataSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
@Slf4j
public class DataAdminController {

	// 데이터 소스 관련 서비스
	private final GoogleDriveDataSyncService googleDriveDataSyncService;
	private final NotificationService notificationService;

	// 데이터 유지보수 관련 서비스
	private final GameStatusAnalyzer gameStatusAnalyzer;
	private final GameCleanupService gameCleanupService;
	private final DataReconciliationService reconciliationService;
	private final DataVerificationService verificationService;

	// Google Drive 서비스
	private final DriveTestService driveTestService;
	
	// LoL Esports 서비스
	private final com.toy.nar.app.lolesports.LeagueMatchService leagueMatchService;

	// == 데이터 동기화 (Sync) ==
	@PostMapping("/sync/matches/history")
	public ResponseEntity<Map<String, Object>> syncMatchHistory() {
		log.info("Requesting full match history sync for ALL target leagues");
		
		int syncedCount = leagueMatchService.syncAllLeaguesFullHistory();
		
		return ResponseEntity.ok(Map.of(
			"success", true,
			"syncedCount", syncedCount,
			"message", "모든 리그의 전체 히스토리 동기화가 완료되었습니다."
		));
	}

	@PostMapping("/sync")
	public ResponseEntity<Map<String, Object>> syncData() {
		log.info("Google Drive sync requested via API");

		DataSyncResult result = googleDriveDataSyncService.syncFromGoogleDrive();

		String errorMessage = result.errorMessage() != null ? result.errorMessage() : "오류 메시지 없음";
		String summary = result.getSummary() != null ? result.getSummary() : "요약 정보 없음";
		String source = result.source() != null ? result.source() : "출처 정보 없음";


		if (result.isSuccess()) {
			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "동기화가 성공적으로 완료되었습니다",
				"summary", summary,
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
				"error", errorMessage,
				"summary", result.getSummary() != null ? result.getSummary() : "요약 정보 없음",
				"data", Map.of(
					"totalRowsProcessed", result.totalRowsProcessed(),
					"processingTimeMs", result.processingTimeMs(),
					"source", source
				)
			));
		}
	}

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

	// == 데이터 정합성 검사 (Integrity) ==
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> getDataStatus() {
		try {
			GameStatusAnalyzer.GameStatusReport report = gameStatusAnalyzer.analyzeGameStatus();

			Map<String, Object> response = new HashMap<>();
			response.put("totalGames", report.totalGames());
			response.put("completeGames", report.completeGames());
			response.put("incompleteGames", report.incompleteGames());
			response.put("needsRepair", report.incompleteGames() > 0);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Failed to analyze data status", e);
			return ResponseEntity.status(500)
				.body(Map.of("error", "데이터 상태 분석 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	@PostMapping("/cleanup-incomplete")
	public ResponseEntity<Map<String, Object>> deleteIncompleteGames() {
		try {
			CleanupResult result = gameCleanupService.deleteIncompleteGames();

			Map<String, Object> response = new HashMap<>();
			response.put("success", result.success());
			response.put("deletedGames", result.deletedGameIds());
			response.put("deletedGameIds", result.deletedGameIds());
			response.put("message", result.message());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Failed to delete incomplete games", e);
			return ResponseEntity.status(500)
				.body(Map.of("error", "불완전한 게임 삭제 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	@PostMapping("/reconcile-leagueteams")
	public ResponseEntity<DataReconciliationService.ReconciliationResult> reconcileLeagueTeams() {
		DataReconciliationService.ReconciliationResult result = reconciliationService.reconcileLeagueTeams();
		return ResponseEntity.ok(result);
	}

	// == 데이터 검증 (Verification) ==
	@GetMapping("/verify-full-report")
	public ResponseEntity<DataVerificationService.VerificationReport> getFullVerificationReport() {
		DataVerificationService.VerificationReport report = verificationService.verifyAllData();
		return ResponseEntity.ok(report);
	}

	@GetMapping("/drive/metadata")
	public ResponseEntity<String> testMetadata() {
		driveTestService.testFileMetadata();
		return ResponseEntity.ok("메타데이터 테스트 완료 - 로그 확인");
	}

	@GetMapping("/drive/download")
	public ResponseEntity<String> testDownload() {
		driveTestService.testCsvDownload();
		return ResponseEntity.ok("다운로드 테스트 완료 - 로그 확인");
	}

	@GetMapping("/drive/all")
	public ResponseEntity<String> testAll() {
		driveTestService.runAllTests();
		return ResponseEntity.ok("전체 테스트 완료 - 로그 확인");
	}

	@PostMapping("/test-notification")
	public ResponseEntity<String> testNotification() {
		notificationService.sendSuccessNotification(
			DataSyncResult.success()
				.toBuilder()
				.source("TEST")
				.processingTimeMs(100)
				.build()
		);
		return ResponseEntity.ok("Slack 알림 완료");
	}

}