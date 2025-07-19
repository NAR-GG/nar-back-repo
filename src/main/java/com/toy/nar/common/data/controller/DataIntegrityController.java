package com.toy.nar.common.data.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.common.data.GameStatusAnalyzer;
import com.toy.nar.common.data.dto.CleanupResult;
import com.toy.nar.common.data.dto.DataIngestionResult;
import com.toy.nar.common.data.dto.RepairResult;
import com.toy.nar.common.data.service.DataIngestionFacade;
import com.toy.nar.common.data.service.DataReconciliationService;
import com.toy.nar.common.data.service.GameCleanupService;
import com.toy.nar.common.data.service.SelectiveDataRepairService;
import com.toy.nar.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/data-integrity")
@RequiredArgsConstructor
@Slf4j
public class DataIntegrityController {

	private final GameStatusAnalyzer gameAnalyzer;
	private final SelectiveDataRepairService repairService;
	private final DataIngestionFacade ingestionFacade;
	private final GameRepository gameRepository;
	private final GameCleanupService cleanupService;
	private final DataReconciliationService reconciliationService;

	/**
	 * 현재 게임 데이터 상태 분석
	 */
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> getDataStatus() {
		try {
			GameStatusAnalyzer.GameStatusReport report = gameAnalyzer.analyzeGameStatus();

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

	/**
	 * 불완전한 게임 수정
	 */
	@PostMapping("/repair")
	public ResponseEntity<Map<String, Object>> repairIncompleteGames() {
		try {
			log.info("🔧 Starting data repair via API...");
			RepairResult result = repairService.repairIncompleteGamesFromCsv();

			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("initialIncompleteGames", result.getInitialIncompleteGames());
			response.put("repairedGames", result.getRepairedGames());
			response.put("failedGames", result.getFailedGames());
			response.put("notFoundGames", result.getNotFoundGames());
			response.put("processingTime", result.getProcessingTime());

			if (result.getFailedGames() > 0) {
				response.put("failedGameDetails", result.getFailedGameDetails());
			}

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Failed to repair incomplete games", e);
			return ResponseEntity.status(500)
				.body(Map.of("error", "데이터 수정 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	/**
	 * 전체 데이터 초기 로드 (데이터가 없을 때만)
	 */
	@PostMapping("/initial-load")
	public ResponseEntity<Map<String, Object>> initialDataLoad() {
		try {
			long existingGames = gameRepository.count();

			if (existingGames > 0) {
				return ResponseEntity.badRequest()
					.body(Map.of("error", "데이터가 이미 존재합니다. /repair 엔드포인트를 사용하세요."));
			}

			log.info("🚀 Starting initial data load via API...");
			DataIngestionResult result = ingestionFacade.ingestCsvData();

			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("processedRows", result.processedRows());
			response.put("processedGames", result.processedGames());
			response.put("successfulGames", result.successfulGames());
			response.put("failedGames", result.failedGames());
			response.put("skippedGames", result.skippedGames());
			response.put("incompleteGames", result.incompleteGames());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Failed initial data load", e);
			return ResponseEntity.status(500)
				.body(Map.of("error", "초기 데이터 로드 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	/**
	 * 불완전한 게임 목록 조회
	 */
	@GetMapping("/incomplete-games")
	public ResponseEntity<Map<String, Object>> getIncompleteGames() {
		try {
			GameStatusAnalyzer.GameStatusReport report = gameAnalyzer.analyzeGameStatus();

			Map<String, Object> response = new HashMap<>();
			response.put("incompleteGameIds", report.incompleteGameIds());
			response.put("count", report.totalGames());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Failed to get incomplete games", e);
			return ResponseEntity.status(500)
				.body(Map.of("error", "불완전한 게임 조회 중 오류가 발생했습니다: " + e.getMessage()));
		}
	}

	@PostMapping("/delete-incomplete")
	public ResponseEntity<Map<String, Object>> deleteIncompleteGames() {
		try {
			CleanupResult result = cleanupService.deleteIncompleteGames();

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
}