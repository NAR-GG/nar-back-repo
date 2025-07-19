package com.toy.nar.domain.combination;

import java.time.LocalDate;
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
	private final LocalDate latestGameDate;
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
			latestGameDate.isAfter(LocalDate.now().minusDays(30));
	}

	public int compareByRecency(ChampionCombination other) {
		if (this.frequency != other.frequency) {
			return Long.compare(other.frequency, this.frequency);
		}
		return other.latestGameDate.compareTo(this.latestGameDate);
	}

	public boolean containsAll(Collection<String> championNames) {
		return champions.containsAll(championNames);
	}

	public int size() {
		return champions.size();
	}
}