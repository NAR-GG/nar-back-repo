package com.toy.nar.app.data.ingestion.dto;

public record DataIngestionResult(
	long processedRows,
	int processedGames,
	int successfulGames,
	int failedGames,
	int skippedGames,
	int incompleteGames,
	long processingTimeMs
) {

	public static Builder builder() {
		return new Builder();
	}

	public String getSummary() {
		return String.format(
			"Processed: %d rows, %d games | Success: %d | Failed: %d | Skipped: %d | Invalid: %d | Time: %dms",
			processedRows, processedGames, successfulGames, failedGames, skippedGames, incompleteGames, processingTimeMs
		);
	}

	public static class Builder {
		private long processedRows = 0;
		private int processedGames = 0;
		private int successfulGames = 0;
		private int failedGames = 0;
		private int skippedGames = 0;
		private int incompleteGames = 0;
		private long processingTimeMs = 0;

		public Builder incrementProcessedRows() {
			this.processedRows++;
			return this;
		}

		public Builder merge(ChunkProcessingResult chunkResult) {
			this.processedGames   += chunkResult.validGames();
			this.successfulGames  += chunkResult.validGames();
			this.failedGames      += chunkResult.failedGames();
			this.skippedGames     += chunkResult.skippedGames();
			this.incompleteGames  += chunkResult.invalidGames();
			return this;
		}

		// 새로 추가된 setter
		public Builder processingTimeMs(long ms) {
			this.processingTimeMs = ms;
			return this;
		}

		public DataIngestionResult build() {
			return new DataIngestionResult(
				processedRows,
				processedGames,
				successfulGames,
				failedGames,
				skippedGames,
				incompleteGames,
				processingTimeMs
			);
		}
	}
}
