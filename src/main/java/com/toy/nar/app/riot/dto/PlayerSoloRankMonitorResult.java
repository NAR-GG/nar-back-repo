package com.toy.nar.app.riot.dto;

public record PlayerSoloRankMonitorResult(
		int totalTrackedAccounts,
		int checkedCount,
		int noRecentMatchCount,
		int unchangedCount,
		int otherQueueCount,
		int rankedSoloCount,
		int alertsSentCount,
		int failedCount) {
}
