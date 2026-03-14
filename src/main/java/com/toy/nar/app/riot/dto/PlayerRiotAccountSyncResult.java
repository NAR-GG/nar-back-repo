package com.toy.nar.app.riot.dto;

import java.util.List;

public record PlayerRiotAccountSyncResult(
		int totalPlayers,
		int syncedCount,
		int skippedCount,
		int failedCount,
		List<String> skippedPlayers,
		List<String> failedPlayers) {
}
