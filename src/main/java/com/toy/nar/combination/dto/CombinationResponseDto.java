package com.toy.nar.combination.dto;

import java.time.LocalDate;
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
	List<String> recentPatches
) {
	public double calculateWinRate() {
		long totalGames = winCount + lossCount;
		return totalGames > 0 ? (double) winCount / totalGames * 100 : 0.0;
	}
}
