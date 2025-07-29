package com.toy.nar.domain.combination;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.toy.nar.common.util.NameNormalizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@Builder
@Getter
@AllArgsConstructor
public class TeamComposition {
	private final Long gameId;
	private final String teamName;
	private final List<String> champions;
	private final boolean isWin;
	private final String patch;
	private final String league;
	private final LocalDateTime gameDate;

	public boolean isValidTeam() {
		return champions.size() == 5;
	}

	public boolean containsChampion(String championName) {
		boolean directMatch = champions.contains(championName);
		boolean caseInsensitiveMatch = champions.stream()
			.anyMatch(champion -> champion.equalsIgnoreCase(championName));
		boolean normalizedMatch = champions.stream()
			.anyMatch(champion ->
				NameNormalizer.normalizeChampionName(champion)
					.equalsIgnoreCase(NameNormalizer.normalizeChampionName(championName)));

		log.debug("🔍 Checking '{}' in {}: direct={}, case={}, normalized={}",
			championName, champions, directMatch, caseInsensitiveMatch, normalizedMatch);

		return directMatch || caseInsensitiveMatch || normalizedMatch;
	}
}
