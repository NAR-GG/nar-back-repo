package com.toy.nar.app.analysis.dto;

import java.util.Locale;

public enum TeamScatterMetric {
	ALL("Average per Game"),
	KILLS("Average Kills per Game"),
	GOLD("Average Gold per Game"),
	OBJECTIVES("Average Objectives per Game");

	private final String axisLabel;

	TeamScatterMetric(String axisLabel) {
		this.axisLabel = axisLabel;
	}

	public String getAxisLabel() {
		return axisLabel;
	}

	public static TeamScatterMetric from(String value) {
		if (value == null || value.isBlank()) {
			return ALL;
		}
		try {
			return TeamScatterMetric.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported metric: " + value);
		}
	}
}
