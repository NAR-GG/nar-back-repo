package com.toy.nar.app.lolesports.live;

import java.time.LocalDateTime;

public record ActiveLiveGame(
		String gameId,
		String matchId,
		String leagueName,
		String blueTeamName,
		String redTeamName,
		LocalDateTime lastSeenAtUtc,
		int consecutiveFailures) {

	public ActiveLiveGame withLastSeenAt(LocalDateTime seenAtUtc) {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, seenAtUtc, consecutiveFailures);
	}

	public ActiveLiveGame increaseFailures() {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, lastSeenAtUtc, consecutiveFailures + 1);
	}

	public ActiveLiveGame clearFailures() {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, lastSeenAtUtc, 0);
	}

	public ActiveLiveGame mergeMissingMetadata(ActiveLiveGame metadata) {
		if (metadata == null) {
			return this;
		}
		return new ActiveLiveGame(
				firstUseful(gameId, metadata.gameId()),
				firstUseful(matchId, metadata.matchId()),
				firstUseful(leagueName, metadata.leagueName()),
				firstDisplayName(blueTeamName, metadata.blueTeamName()),
				firstDisplayName(redTeamName, metadata.redTeamName()),
				lastSeenAtUtc,
				consecutiveFailures);
	}

	private String firstUseful(String current, String fallback) {
		return isBlank(current) ? fallback : current;
	}

	private String firstDisplayName(String current, String fallback) {
		if (isBlank(current) || isNumericIdentifier(current)) {
			return fallback;
		}
		return current;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private boolean isNumericIdentifier(String value) {
		return value != null && value.length() >= 12 && value.chars().allMatch(Character::isDigit);
	}
}
