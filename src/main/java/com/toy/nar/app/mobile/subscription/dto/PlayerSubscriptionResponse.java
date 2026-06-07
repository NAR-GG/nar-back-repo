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
		boolean subscribed) {

	public static PlayerSubscriptionResponse from(
			PlayerRepository.LckPlayerOption player,
			boolean subscribed) {
		return new PlayerSubscriptionResponse(
				player.getPlayerId(),
				player.getPlayerName(),
				player.getPlayerImageUrl(),
				player.getRole(),
				player.getTeamId(),
				player.getTeamCode(),
				player.getTeamName(),
				player.getTeamImageUrl(),
				subscribed);
	}
}
