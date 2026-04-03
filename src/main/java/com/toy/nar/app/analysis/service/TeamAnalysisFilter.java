package com.toy.nar.app.analysis.service;

import java.util.Locale;

record TeamAnalysisFilter(
		String leagueName,
		Integer year,
		String split,
		String patch,
		String side) {

	static TeamAnalysisFilter from(String league, Integer year, String split, String patch, String side) {
		return new TeamAnalysisFilter(
				normalizeLeague(league),
				year != null ? year : 2026,
				normalizeBlank(split),
				normalizeBlank(patch),
				normalizeSide(side));
	}

	private static String normalizeLeague(String league) {
		if (league == null || league.isBlank()) {
			return "LCK";
		}
		return league.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeSide(String side) {
		if (side == null || side.isBlank() || "ALL".equalsIgnoreCase(side)) {
			return null;
		}
		return side.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
