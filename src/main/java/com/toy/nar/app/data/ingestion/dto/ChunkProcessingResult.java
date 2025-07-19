package com.toy.nar.app.data.ingestion.dto;

public record ChunkProcessingResult(
	int validGames,
	int invalidGames,
	int skippedGames,
	int failedGames
) {
	public static ChunkProcessingResult of(int valid, int invalid, int skipped, int failed) {
		return new ChunkProcessingResult(valid, invalid, skipped, failed);
	}

	public static ChunkProcessingResult empty() {
		return new ChunkProcessingResult(0, 0, 0, 0);
	}
}
