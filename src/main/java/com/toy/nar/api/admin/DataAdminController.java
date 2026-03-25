package com.toy.nar.api.admin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.data.maintenance.GameStatusAnalyzer;
import com.toy.nar.app.data.maintenance.PlayerImageMigrationService;
import com.toy.nar.app.data.maintenance.dto.CleanupResult;
import com.toy.nar.app.data.source.DriveTestService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.data.source.dto.DataSyncResult;
import com.toy.nar.app.data.maintenance.DataReconciliationService;
import com.toy.nar.app.data.maintenance.DataVerificationService;
import com.toy.nar.app.data.maintenance.GameCleanupService;
import com.toy.nar.app.data.source.GoogleDriveDataSyncService;
import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.schedule.CacheEvictionService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Profile({ "local", "dev", "prod" })
@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
@Hidden
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
	private final PlayerImageMigrationService playerImageMigrationService;

	// Google Drive 서비스
	private final DriveTestService driveTestService;

	// LoL Esports 서비스
	private final com.toy.nar.app.lolesports.LeagueMatchService leagueMatchService;
	private final CacheEvictionService cacheEvictionService;

	// == 데이터 동기화 (Sync) ==
	@PostMapping("/sync/matches/history")
	public ResponseEntity<Map<String, Object>> syncMatchHistory() {
		log.info("Requesting full match history sync for ALL target leagues");

		int syncedCount = leagueMatchService.syncAllLeaguesFullHistory();
		cacheEvictionService.evictScheduleCaches();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"syncedCount", syncedCount,
				"message", "모든 리그의 전체 히스토리 동기화가 완료되었습니다."));
	}

	@PostMapping("/sync/matches/quick")
	public ResponseEntity<Map<String, Object>> syncMatchesQuick(
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "LCK") String league,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean includeTeamMetadata) {
		log.info("Quick sync requested for league: {}, includeTeamMetadata={}", league, includeTeamMetadata);

		long start = System.currentTimeMillis();
		leagueMatchService.syncMatches(league, includeTeamMetadata);
		long elapsed = System.currentTimeMillis() - start;
		cacheEvictionService.evictScheduleCaches();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"league", league,
				"includeTeamMetadata", includeTeamMetadata,
				"elapsedMs", elapsed,
				"message", league + " 최신 동기화가 완료되었습니다."));
	}

	@PostMapping("/sync/matches/recent-backfill")
	public ResponseEntity<Map<String, Object>> backfillRecentMatches(
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "LCK") String league,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean includeTeamMetadata) {
		log.info("Recent match backfill requested for league: {}, includeTeamMetadata={}", league, includeTeamMetadata);

		long start = System.currentTimeMillis();
		com.toy.nar.app.lolesports.LeagueMatchService.RecentMatchBackfillResult result = leagueMatchService
				.backfillRecentMatches(league, includeTeamMetadata);
		long elapsed = System.currentTimeMillis() - start;
		cacheEvictionService.evictScheduleCaches();

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("league", result.league());
		response.put("includeTeamMetadata", result.includeTeamMetadata());
		response.put("fetchedMatches", result.fetchedMatches());
		response.put("insertedMatches", result.insertedMatches());
		response.put("updatedMatches", result.updatedMatches());
		response.put("skippedMatches", result.skippedMatches());
		response.put("metadataUpdated", result.metadataUpdated());
		response.put("pageMatchCount", result.pageMatchCount());
		response.put("teamIdentityCreated", result.teamIdentityCreated());
		response.put("teamIdentityUpdated", result.teamIdentityUpdated());
		response.put("teamIdentityUnresolved", result.teamIdentityUnresolved());
		response.put("gameIdUpdated", result.gameIdUpdated());
		response.put("gameIdUnchanged", result.gameIdUnchanged());
		response.put("gameIdFailed", result.gameIdFailed());
		response.put("gameIdentityCreated", result.gameIdentityCreated());
		response.put("gameIdentityUpdated", result.gameIdentityUpdated());
		response.put("gameIdentityUnresolved", result.gameIdentityUnresolved());
		response.put("elapsedMs", elapsed);
		response.put("message", league + " recent match backfill completed");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/cache/evict-schedule")
	public ResponseEntity<Map<String, Object>> evictScheduleCaches() {
		long start = System.currentTimeMillis();
		cacheEvictionService.evictScheduleCaches();
		long elapsed = System.currentTimeMillis() - start;

		return ResponseEntity.ok(Map.of(
				"success", true,
				"elapsedMs", elapsed,
				"message", "일정/매치 상세 캐시 무효화가 완료되었습니다."));
	}

	@PostMapping("/sync/matches/team-identities")
	public ResponseEntity<Map<String, Object>> backfillTeamExternalIdentities(
			@org.springframework.web.bind.annotation.RequestParam(required = false) String leagues,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean fullHistory,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int maxPages) {
		List<String> targetLeagues = parseLeagues(leagues);
		log.info("Team external identity backfill requested. leagues={}, fullHistory={}, maxPages={}",
				targetLeagues, fullHistory, maxPages);

		long start = System.currentTimeMillis();
		com.toy.nar.app.lolesports.LeagueMatchService.TeamIdentityBackfillResult result = leagueMatchService
				.backfillTeamExternalIdentities(targetLeagues, fullHistory, maxPages);
		long elapsed = System.currentTimeMillis() - start;

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("leagues", result.leagues());
		response.put("fetchedPages", result.fetchedPages());
		response.put("fullHistory", fullHistory);
		response.put("maxPages", maxPages);
		response.put("discoveredExternalTeams", result.discoveredExternalTeams());
		response.put("createdMappings", result.createdMappings());
		response.put("updatedMappings", result.updatedMappings());
		response.put("unresolvedMappings", result.unresolvedMappings());
		response.put("conflicts", result.conflicts());
		response.put("elapsedMs", elapsed);
		response.put("message", "team external identity backfill completed");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/sync/matches/league-match-team-ids")
	public ResponseEntity<Map<String, Object>> backfillLeagueMatchExternalTeamIds(
			@org.springframework.web.bind.annotation.RequestParam(required = false) String leagues) {
		List<String> targetLeagues = parseLeagues(leagues);
		log.info("LeagueMatch external team id backfill requested. leagues={}", targetLeagues);

		long start = System.currentTimeMillis();
		com.toy.nar.app.lolesports.LeagueMatchService.LeagueMatchExternalTeamIdBackfillResult result = leagueMatchService
				.backfillLeagueMatchExternalTeamIds(targetLeagues);
		long elapsed = System.currentTimeMillis() - start;
		cacheEvictionService.evictScheduleCaches();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"leagues", result.leagues(),
				"targetMatches", result.targetMatches(),
				"updatedMatches", result.updatedMatches(),
				"unresolvedMatches", result.unresolvedMatches(),
				"elapsedMs", elapsed,
				"message", "league match external team id backfill completed"));
	}

	@PostMapping("/sync/matches/game-identities")
	public ResponseEntity<Map<String, Object>> backfillGameExternalIdentities(
			@org.springframework.web.bind.annotation.RequestParam(required = false) String leagues,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "2025") int year) {
		List<String> targetLeagues = parseLeagues(leagues);
		log.info("Game external identity backfill requested. leagues={}, year={}", targetLeagues, year);

		long start = System.currentTimeMillis();
		com.toy.nar.app.lolesports.LeagueMatchService.GameExternalIdentityBackfillResult result = leagueMatchService
				.backfillGameExternalIdentities(targetLeagues, year);
		long elapsed = System.currentTimeMillis() - start;
		cacheEvictionService.evictScheduleCaches();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"leagues", result.leagues(),
				"targetMatches", result.targetMatches(),
				"targetGameRows", result.targetGameRows(),
				"createdMappings", result.createdMappings(),
				"updatedMappings", result.updatedMappings(),
				"unresolvedMappings", result.unresolvedMappings(),
				"conflicts", result.conflicts(),
				"elapsedMs", elapsed,
				"message", "game external identity backfill completed"));
	}

	@PostMapping("/sync/matches/game-ids")
	public ResponseEntity<Map<String, Object>> backfillLeagueMatchGameIds(
			@org.springframework.web.bind.annotation.RequestParam int year,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int limit) {
		log.info("LeagueMatch game id backfill requested. year={}, limit={}", year, limit);

		long start = System.currentTimeMillis();
		com.toy.nar.app.lolesports.LeagueMatchService.GameIdBackfillResult result = leagueMatchService
				.backfillGameIdsForYear(year, limit);
		long elapsed = System.currentTimeMillis() - start;
		cacheEvictionService.evictScheduleCaches();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"year", result.year(),
				"targetMatches", result.targetMatches(),
				"updatedMatches", result.updatedMatches(),
				"unchangedMatches", result.unchangedMatches(),
				"failedMatches", result.failedMatches(),
				"elapsedMs", elapsed,
				"message", "league match game id backfill completed"));
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
							"successRate", result.successRate())));
		} else {
			return ResponseEntity.status(500).body(Map.of(
					"success", false,
					"message", "동기화 중 오류가 발생했습니다",
					"error", errorMessage,
					"summary", result.getSummary() != null ? result.getSummary() : "요약 정보 없음",
					"data", Map.of(
							"totalRowsProcessed", result.totalRowsProcessed(),
							"processingTimeMs", result.processingTimeMs(),
							"source", source)));
		}
	}

	@GetMapping("/file-status")
	public ResponseEntity<Map<String, Object>> checkFileStatus() {
		log.info("📋 File status check requested");

		String fileStatus = googleDriveDataSyncService.checkFileStatus();

		return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "파일 상태 확인 완료",
				"fileStatus", fileStatus));
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
						.build());
		return ResponseEntity.ok("Slack 알림 완료");
	}

	@PostMapping("/sync/player-images")
	public ResponseEntity<Map<String, Object>> syncPlayerImages() {
		log.info("Starting player image migration...");
		Map<String, Object> result = playerImageMigrationService.migratePlayerImages();
		return ResponseEntity.ok(result);
	}

	private List<String> parseLeagues(String leagues) {
		if (leagues == null || leagues.isBlank()) {
			return LeagueConstants.TARGET_LEAGUES;
		}
		List<String> parsed = java.util.Arrays.stream(leagues.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.map(String::toUpperCase)
				.distinct()
				.toList();
		return parsed.isEmpty() ? LeagueConstants.TARGET_LEAGUES : parsed;
	}
}
