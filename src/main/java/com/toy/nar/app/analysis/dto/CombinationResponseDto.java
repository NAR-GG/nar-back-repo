package com.toy.nar.app.analysis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CombinationResponseDto(
	String combinationId,
	int rank,
	List<String> champions,
	long frequency,
	long winCount,
	long lossCount,
	double winRate,
	LocalDate latestGameDate,
	String latestPatch
) {
	public double calculateWinRate() {
		long totalGames = winCount + lossCount;
		return totalGames > 0 ? (double) winCount / totalGames * 100 : 0.0;
	}
}
