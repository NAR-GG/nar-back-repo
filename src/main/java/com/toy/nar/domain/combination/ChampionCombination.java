package com.toy.nar.domain.combination;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChampionCombination {
	private final List<String> champions;
	private final long frequency;
	private final long winCount;
	private final long lossCount;
	private final LocalDateTime latestGameDate;
	private final Set<Long> gameIds;
	private final Set<String> patches;
	private final Set<String> leagues;
	private final Set<String> teams;

	public double getWinRate() {
		long totalGames = winCount + lossCount;
		return totalGames > 0 ? (double) winCount / totalGames : 0.0;
	}

	public boolean contains(String championName) {
		return champions.contains(championName);
	}

	public boolean isRecentCombination() {
		return latestGameDate != null &&
			latestGameDate.isAfter(LocalDateTime.now().minusDays(30));
	}

	public static int compareByFrequency(ChampionCombination a, ChampionCombination b) {
		if (a.frequency != b.frequency) {
			return Long.compare(b.getFrequency(), a.getFrequency());
		}
		return b.latestGameDate.compareTo(a.latestGameDate);
	}

	public static int compareByPatch(ChampionCombination a, ChampionCombination b) {
		String patchA = a.getPatches().stream().max(String::compareTo).orElse("");
		String patchB = b.getPatches().stream().max(String::compareTo).orElse("");
		int patchCompare = patchB.compareTo(patchA);  // DESC
		if (patchCompare != 0) {
			return patchCompare;
		}
		if (a.frequency != b.frequency) {
			return Long.compare(b.getFrequency(), a.getFrequency());
		}
		return b.latestGameDate.compareTo(a.latestGameDate);
	}

	public static int compareByRecency(ChampionCombination a, ChampionCombination b) {
		int dateCompare = b.latestGameDate.compareTo(a.latestGameDate);  // DESC (최신 먼저)
		if (dateCompare != 0) {
			return dateCompare;
		}
		return Long.compare(b.getFrequency(), a.getFrequency());
	}

	public int size() {
		return champions.size();
	}
}