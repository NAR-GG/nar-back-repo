package com.toy.nar.app.data.source.dto;

import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;

import lombok.Builder;

@Builder(toBuilder = true)
public record DataSyncResult(
	long totalRowsProcessed,
	int newGamesAdded,
	int skippedGames,
	int invalidGames,
	int failedGames,
	long processingTimeMs,
	String status,
	String errorMessage,
	String source
) {

	public boolean isSuccess() {
		return failedGames == 0 && errorMessage == null;
	}

	public double successRate() {
		int totalGames = newGamesAdded + failedGames + skippedGames;
		return totalGames > 0 ? (double) newGamesAdded / totalGames * 100 : 0.0;
	}

	public String getSummary() {
		return String.format("Sync from %s: %d rows processed, %d new games added, %d skipped, %d failed in %dms",
			source, totalRowsProcessed, newGamesAdded, skippedGames, failedGames, processingTimeMs);
	}

	// 🔥 기본값을 가진 빌더 생성 메서드
	public static DataSyncResultBuilder defaultBuilder() {
		return DataSyncResult.builder()
			.totalRowsProcessed(0L)
			.newGamesAdded(0)
			.skippedGames(0)
			.invalidGames(0)
			.failedGames(0)
			.processingTimeMs(0L)
			.status("SUCCESS")
			.source("GOOGLE_DRIVE");
	}

	// 정적 팩토리 메서드들
	public static DataSyncResult fromIngestionResult(DataIngestionResult ingestionResult) {
		return defaultBuilder()
			.totalRowsProcessed(ingestionResult.processedRows())
			.newGamesAdded(ingestionResult.successfulGames())
			.skippedGames(ingestionResult.skippedGames())
			.invalidGames(ingestionResult.incompleteGames())
			.failedGames(ingestionResult.failedGames())
			.processingTimeMs(ingestionResult.processingTimeMs())
			.status(ingestionResult.failedGames() > 0 ? "COMPLETED_WITH_ERRORS" : "SUCCESS")
			.build();
	}

	public static DataSyncResult failure(String errorMessage) {
		return defaultBuilder()
			.status("FAILED")
			.errorMessage(errorMessage)
			.build();
	}

	public static DataSyncResult success() {
		return defaultBuilder()
			.status("SUCCESS")
			.build();
	}
}
