package com.toy.nar.app.analysis.dto;

import java.time.LocalDate;
import java.util.List;

public record CombinationDetailDto(
	CombinationResponseDto summary,
	List<GameDetailDto> gameDetails
) {
	public record GameDetailDto(
		Long gameId,
		LocalDate gameDate,
		String split,
		String league,
		String patch,
		int gameLengthSeconds,
		TeamDetailDto ourTeam,        // 🔥 우리 팀 정보
		TeamDetailDto opponentTeam    // 🔥 상대 팀 정보
	) {}

	public record TeamDetailDto(
		String teamName,
		String side,                  // Blue/Red
		boolean isWin,               // 승/패
		List<PlayerDetailDto> players
	) {}

	public record PlayerDetailDto(
		String position,
		String championName,
		String playerName
	) {}
}

