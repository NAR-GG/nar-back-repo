package com.toy.nar.domain.participant;

import java.util.List;

public final class LckTeamCatalog {

	public static final List<String> TEAM_CODES = List.of(
			"T1", "HLE", "GEN", "DK", "KT",
			"DNS", "BFX", "NS", "BRO", "KRX");

	private LckTeamCatalog() {
	}

	public static boolean contains(String teamCode) {
		return teamCode != null && TEAM_CODES.stream().anyMatch(code -> code.equalsIgnoreCase(teamCode));
	}

	public static int orderOf(String teamCode) {
		if (teamCode == null) {
			return TEAM_CODES.size();
		}
		for (int index = 0; index < TEAM_CODES.size(); index++) {
			if (TEAM_CODES.get(index).equalsIgnoreCase(teamCode)) {
				return index;
			}
		}
		return TEAM_CODES.size();
	}
}
