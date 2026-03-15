package com.toy.nar.app.riot.dto;

public record PlayerRiotAlertCheckResult(
		String puuid,
		boolean currentGameFound,
		boolean rankedSolo,
		boolean notificationSent,
		String gameId,
		Integer queueId,
		String queueName,
		String riotId,
		String championName,
		String championIconUrl,
		String status) {
}
