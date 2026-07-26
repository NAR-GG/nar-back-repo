package com.toy.nar.app.riot.dto;

public record PlayerSoloRankMatchFallbackResult(
		int totalTrackedAccounts,
		int checkedCount,
		int newGameCount,
		int alertsSentCount,
		int failedCount) {
}
