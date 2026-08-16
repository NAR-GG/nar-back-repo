package com.toy.nar.app.mobile.subscription.dto;

import com.toy.nar.domain.participant.repository.PlayerRepository;

public record PlayerSubscriptionResponse(
		Long playerId,
		String playerName,
		String playerImageUrl,
		String role,
		Long teamId,
		String teamCode,
		String teamName,
		String teamImageUrl,
		boolean subscribed,
		boolean startEnabled,
		boolean endEnabled) {

	/** 목록/구독 응답의 기본값 — 시작 ON, 종료 OFF. */
	public static PlayerSubscriptionResponse from(
			PlayerRepository.LckPlayerOption player,
			boolean subscribed) {
		return from(player, subscribed, true, false);
	}

	public static PlayerSubscriptionResponse from(
			PlayerRepository.LckPlayerOption player,
			boolean subscribed,
			boolean startEnabled,
			boolean endEnabled) {
		return new PlayerSubscriptionResponse(
				player.getPlayerId(),
				player.getPlayerName(),
				player.getPlayerImageUrl(),
				player.getRole(),
				player.getTeamId(),
				player.getTeamCode(),
				player.getTeamName(),
				player.getTeamImageUrl(),
				subscribed,
				startEnabled,
				endEnabled);
	}
}
