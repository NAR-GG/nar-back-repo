package com.toy.nar.app.analysis.dto;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MultiCombinationFilterDto {
	private final Integer year;
	private final List<String> splits;
	private final List<String> leagueNames;
	private final List<String> teamNames;
	private final String patch;

	public static MultiCombinationFilterDto from(CombinationFilterDto legacyFilter) {
		return MultiCombinationFilterDto.builder()
			.year(legacyFilter.year())
			.splits(legacyFilter.split() != null ? List.of(legacyFilter.split()) : Collections.emptyList())
			.leagueNames(legacyFilter.leagueName() != null ? List.of(legacyFilter.leagueName()) : Collections.emptyList())
			.teamNames(legacyFilter.teamName() != null ? List.of(legacyFilter.teamName()) : Collections.emptyList())
			.patch(legacyFilter.patch())
			.build();
	}

	public boolean hasMultipleFilters() {
		return (splits != null && splits.size() > 1) ||
			(leagueNames != null && leagueNames.size() > 1) ||
			(teamNames != null && teamNames.size() > 1);
	}

	public boolean isEmpty() {
		return (splits == null || splits.isEmpty()) &&
			(leagueNames == null || leagueNames.isEmpty()) &&
			(teamNames == null || teamNames.isEmpty()) &&
			year == null &&
			patch == null;
	}
}