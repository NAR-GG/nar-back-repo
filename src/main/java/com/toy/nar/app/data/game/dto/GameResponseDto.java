package com.toy.nar.app.data.game.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GameResponseDto(
	Long gameId,
	String league,
	String patch,
	LocalDateTime gameDate,
	int gameLengthSeconds,
	TeamInGameDto blueTeam,
	TeamInGameDto redTeam
) {
	public record TeamInGameDto(
		String teamName,
		boolean isWin,
		String side,
		List<PlayerInGameDto> players
	) {}

	public record PlayerInGameDto(
		String playerName,
		String championName,
		String position
	) {}
}
