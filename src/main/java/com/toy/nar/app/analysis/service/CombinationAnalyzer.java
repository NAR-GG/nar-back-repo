package com.toy.nar.app.analysis.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.domain.combination.ChampionCombination;
import com.toy.nar.domain.combination.TeamComposition;
import com.toy.nar.common.util.NameNormalizer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CombinationAnalyzer {

	public List<ChampionCombination> findTopCombinations(
		List<TeamComposition> compositions,
		List<String> targetChampions) {

		List<TeamComposition> validTeams = compositions.stream()
			.filter(TeamComposition::isValidTeam)
			.collect(Collectors.toList());

		List<TeamComposition> filteredTeams = validTeams.stream()
			.filter(comp -> containsAllChampions(comp, targetChampions))
			.collect(Collectors.toList());

		Map<List<String>, CombinationStats> combinationStats = calculateCombinationStats(filteredTeams);

		return combinationStats.entrySet().stream()
			.map(entry -> createChampionCombination(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private Map<List<String>, CombinationStats> calculateCombinationStats(List<TeamComposition> compositions) {
		Map<List<String>, CombinationStats> statsMap = new HashMap<>();

		for (TeamComposition composition : compositions) {
			List<String> sortedChampions = composition.getChampions().stream()
				.sorted()
				.collect(Collectors.toList());

			CombinationStats stats = statsMap.computeIfAbsent(sortedChampions, k -> new CombinationStats());
			stats.addGame(composition);
		}

		return statsMap;
	}

	private ChampionCombination createChampionCombination(List<String> champions, CombinationStats stats) {
		return ChampionCombination.builder()
			.champions(champions)
			.frequency(stats.getFrequency())
			.winCount(stats.getWinCount())
			.lossCount(stats.getLossCount())
			.latestGameDate(stats.getLatestGameDate())
			.gameIds(stats.getGameIds())
			.patches(stats.getPatches())
			.leagues(stats.getLeagues())
			.teams(stats.getTeams())
			.build();
	}

	private boolean containsAllChampions(TeamComposition composition, List<String> champions) {
		return champions.stream()
			.map(NameNormalizer::normalizeChampionName)
			.allMatch(champion -> composition.getChampions().stream()
				.anyMatch(teamChampion ->
					NameNormalizer.normalizeChampionName(teamChampion).equals(champion)));
	}

	@Getter
	private static class CombinationStats {
		private long frequency = 0;
		private long winCount = 0;
		private long lossCount = 0;
		private LocalDateTime latestGameDate = LocalDateTime.MIN;
		private Set<Long> gameIds = new HashSet<>();
		private Set<String> patches = new HashSet<>();
		private Set<String> leagues = new HashSet<>();
		private Set<String> teams = new HashSet<>();

		public void addGame(TeamComposition composition) {
			frequency++;
			gameIds.add(composition.getGameId());

			if (composition.isWin()) {
				winCount++;
			} else {
				lossCount++;
			}

			if (composition.getGameDate().isAfter(latestGameDate)) {
				latestGameDate = composition.getGameDate();
			}

			if (composition.getPatch() != null) {
				patches.add(composition.getPatch());
			}
			if (composition.getLeague() != null) {
				leagues.add(composition.getLeague());
			}
			teams.add(composition.getTeamName());
		}

	}
}