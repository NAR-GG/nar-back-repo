package com.toy.nar.api.admin;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.data.ingestion.DataIngestionFacade;
import com.toy.nar.app.data.ingestion.JdbcBatchDataIngestionFacade;
import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Profile("benchmark")
@RestController
@RequestMapping("/api/admin/benchmark/ingestion")
@RequiredArgsConstructor
@Slf4j
public class DataIngestionBenchmarkController {

	private static final String LOCAL_CSV_RESOURCE = "lol_esports_data.csv";
	private static final List<String> RESET_TABLES = List.of(
			"game_player_stat",
			"game_team_stat",
			"game_participants",
			"bans",
			"game_external_identity",
			"games",
			"sync_status");

	private final DataIngestionFacade dataIngestionFacade;
	private final JdbcBatchDataIngestionFacade jdbcBatchDataIngestionFacade;
	private final JdbcTemplate jdbcTemplate;
	private final DataSource dataSource;
	private final ReentrantLock benchmarkRunLock = new ReentrantLock();

	@PostMapping("/local-csv/jpa-baseline")
	public ResponseEntity<Map<String, Object>> runJpaBaselineWithLocalCsv(
			@RequestParam(defaultValue = "false") boolean reset) {
		return runLocalCsvBenchmark("jpa_baseline", reset, dataIngestionFacade::ingestFromStream);
	}

	@PostMapping("/local-csv/jdbc-batch")
	public ResponseEntity<Map<String, Object>> runJdbcBatchWithLocalCsv(
			@RequestParam(defaultValue = "false") boolean reset) {
		return runLocalCsvBenchmark("jdbc_batch", reset, jdbcBatchDataIngestionFacade::ingestFromStream);
	}

	private ResponseEntity<Map<String, Object>> runLocalCsvBenchmark(
			String mode,
			boolean reset,
			BenchmarkIngestionRunner runner) {
		if (!benchmarkRunLock.tryLock()) {
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", false);
			response.put("mode", mode);
			response.put("error", "Another benchmark ingestion is already running.");
			return ResponseEntity.status(409).body(response);
		}

		long startTime = System.currentTimeMillis();
		log.info("[Benchmark] Starting {} ingestion with local CSV resource: {}", mode, LOCAL_CSV_RESOURCE);

		try (InputStream csvStream = new ClassPathResource(LOCAL_CSV_RESOURCE).getInputStream()) {
			Map<String, Integer> resetCounts = Map.of();
			if (reset) {
				resetCounts = resetBenchmarkTables();
			}

			DataIngestionResult result = runner.run(csvStream, null);
			long elapsedMs = System.currentTimeMillis() - startTime;

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", true);
			response.put("mode", mode);
			response.put("source", LOCAL_CSV_RESOURCE);
			response.put("reset", reset);
			response.put("resetDeletedRows", resetCounts);
			response.put("elapsedMs", elapsedMs);
			response.put("summary", result.getSummary());
			response.put("processedRows", result.processedRows());
			response.put("processedGames", result.processedGames());
			response.put("successfulGames", result.successfulGames());
			response.put("failedGames", result.failedGames());
			response.put("skippedGames", result.skippedGames());
			response.put("incompleteGames", result.incompleteGames());
			response.put("processingTimeMs", result.processingTimeMs());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			long elapsedMs = System.currentTimeMillis() - startTime;
			log.error("[Benchmark] {} ingestion failed", mode, e);

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", false);
			response.put("mode", mode);
			response.put("source", LOCAL_CSV_RESOURCE);
			response.put("elapsedMs", elapsedMs);
			response.put("error", e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		} finally {
			benchmarkRunLock.unlock();
		}
	}

	@PostMapping("/reset")
	public ResponseEntity<Map<String, Object>> resetBenchmarkData() throws SQLException {
		Map<String, Integer> deletedRows = resetBenchmarkTables();

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("database", currentDatabaseName());
		response.put("deletedRows", deletedRows);
		return ResponseEntity.ok(response);
	}

	private Map<String, Integer> resetBenchmarkTables() throws SQLException {
		String databaseName = currentDatabaseName();
		if (databaseName == null || !databaseName.contains("benchmark")) {
			throw new IllegalStateException("Benchmark reset is allowed only for benchmark database. currentDatabase=" + databaseName);
		}

		Map<String, Integer> deletedRows = new LinkedHashMap<>();
		RESET_TABLES.forEach(table -> deletedRows.put(table, countRows(table)));

		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		try {
			for (String table : RESET_TABLES) {
				jdbcTemplate.execute("TRUNCATE TABLE " + table);
			}
		} finally {
			jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
		}

		log.info("[Benchmark] Reset benchmark tables. database={}, deletedRows={}", databaseName, deletedRows);
		return deletedRows;
	}

	private int countRows(String table) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
		return count == null ? 0 : count;
	}

	private String currentDatabaseName() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			return connection.getCatalog();
		}
	}

	@FunctionalInterface
	private interface BenchmarkIngestionRunner {
		DataIngestionResult run(InputStream csvStream, String lastProcessedGameId) throws Exception;
	}
}
