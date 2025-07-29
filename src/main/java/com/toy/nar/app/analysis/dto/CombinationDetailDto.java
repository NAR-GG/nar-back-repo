package com.toy.nar.app.analysis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public record CombinationDetailDto(
	CombinationResponseDto summary,
	List<GameDetailDto> gameDetails
) {
	public record GameDetailDto(
		Long gameId,
		LocalDateTime gameDate,
		String split,
		String league,
		String patch,
		int gameLengthSeconds,
		TeamDetailDto ourTeam,
		TeamDetailDto opponentTeam,
		Optional<Boolean> champion1Won
	) {}

	public record TeamDetailDto(
		String teamName,
		String side,
		boolean isWin,
		List<PlayerDetailDto> players
	) {}

	public record PlayerDetailDto(
		String position,
		String championName,
		String playerName
	) {}
}

