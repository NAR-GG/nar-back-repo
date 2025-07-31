package com.toy.nar.app.analysis.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CombinationStatDto {

	private final List<String> champions;
	private final long frequency;
	private final long winCount;
	private final LocalDateTime latestGameDate;
	private final String latestPatch;

	public CombinationStatDto(String championCombination, long frequency, long winCount, LocalDateTime latestGameDate, String patchVersions) {
		this.champions = List.of(championCombination.split(","));
		this.frequency = frequency;
		this.winCount = winCount;
		this.latestGameDate = latestGameDate;
		this.latestPatch = (patchVersions != null && !patchVersions.isEmpty())
			? patchVersions.split(",")[0]
			: "N/A";
	}

	// Getters
	public List<String> getChampions() { return champions; }
	public long getFrequency() { return frequency; }
	public long getWinCount() { return winCount; }
	public LocalDateTime getLatestGameDate() { return latestGameDate; }
	public String getLatestPatch() { return latestPatch; }
}