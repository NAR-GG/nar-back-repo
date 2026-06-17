package com.toy.nar.app.lolesports.live;

import java.time.LocalDateTime;

public record ActiveLiveGame(
		String gameId,
		String matchId,
		String leagueName,
		String blueTeamName,
		String redTeamName,
		LocalDateTime lastSeenAtUtc,
		int consecutiveFailures,
		// 아래는 라이브 FCM 푸시(#21) 전용 메타데이터. 기존 경로에서는 nullable 이며 사용하지 않는다.
		Integer setNumber,
		String blueEsportsTeamId,
		String redEsportsTeamId) {

	/** 기존 호출부 호환용 생성자. FCM 메타데이터(setNumber/esportsTeamId) 없이 생성한다. */
	public ActiveLiveGame(
			String gameId,
			String matchId,
			String leagueName,
			String blueTeamName,
			String redTeamName,
			LocalDateTime lastSeenAtUtc,
			int consecutiveFailures) {
		this(gameId, matchId, leagueName, blueTeamName, redTeamName, lastSeenAtUtc, consecutiveFailures,
				null, null, null);
	}

	public ActiveLiveGame withLastSeenAt(LocalDateTime seenAtUtc) {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, seenAtUtc, consecutiveFailures,
				setNumber, blueEsportsTeamId, redEsportsTeamId);
	}

	public ActiveLiveGame increaseFailures() {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, lastSeenAtUtc,
				consecutiveFailures + 1, setNumber, blueEsportsTeamId, redEsportsTeamId);
	}

	public ActiveLiveGame clearFailures() {
		return new ActiveLiveGame(gameId, matchId, leagueName, blueTeamName, redTeamName, lastSeenAtUtc, 0,
				setNumber, blueEsportsTeamId, redEsportsTeamId);
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
				consecutiveFailures,
				setNumber != null ? setNumber : metadata.setNumber(),
				firstUseful(blueEsportsTeamId, metadata.blueEsportsTeamId()),
				firstUseful(redEsportsTeamId, metadata.redEsportsTeamId()));
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
