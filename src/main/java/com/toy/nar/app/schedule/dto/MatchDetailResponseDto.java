package com.toy.nar.app.schedule.dto;

import java.util.List;

public record MatchDetailResponseDto(
		MatchSummaryDto summary, // 요약 정보 재사용
		List<GameDetailDto> gameDetails) {
	public record GameDetailDto(
			Long id,
			int gameNumber,
			int gameLengthSeconds,
			String vodUrl, // VOD URL 추가
			TeamPicksDto blueTeam,
			TeamPicksDto redTeam) {
		public record TeamPicksDto(
				String teamName,
				boolean isWin,
				List<String> bans,
				List<PlayerPickDto> players) {
		}

		public record PlayerPickDto(
				String position,
				String playerName,
				String championName) {
		}
	}

}
