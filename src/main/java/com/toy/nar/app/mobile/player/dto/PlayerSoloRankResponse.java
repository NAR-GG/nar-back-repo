package com.toy.nar.app.mobile.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "선수 페이지 솔랭 탭 — 계정·최근 20판·모스트·판별 기록")
public record PlayerSoloRankResponse(
		List<Account> accounts,
		Recent recent,
		@Schema(description = "최근 20판 모스트 상위 5")
		List<Champion> champions,
		@Schema(description = "최근 20판, 최신순. 결과 미수집 판은 뺀다")
		List<Game> games,
		@Schema(description = "최근 7일 솔랭에서 같은 판에 들어온 추적 선수. 함께한 판수 많은 순, 최대 10명")
		List<MetPlayer> metPlayers) {

	public record MetPlayer(
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamCode,
			String teamImageUrl,
			@Schema(description = "같은 편으로 한 판수·그중 이긴 판수")
			int sameTeamGames,
			int sameTeamWins,
			@Schema(description = "상대로 만난 판수와 이 선수 기준 승·패")
			int opponentGames,
			int opponentWins,
			int opponentLosses,
			OffsetDateTime lastMetAt) {
	}

	public record Account(
			@Schema(example = "NA1")
			String platform,
			@Schema(example = "NA")
			String region,
			String riotId,
			@Schema(description = "크롤링 티어 문자열. 모르면 null", example = "Ch 2,256LP")
			String tier,
			@Schema(description = "이 계정으로 지금 솔랭 중")
			boolean live) {
	}

	public record Recent(
			int games,
			int wins,
			int losses,
			double avgKills,
			double avgDeaths,
			double avgAssists,
			double kda,
			@Schema(description = "최근 7일 판수 / 7")
			double gamesPerDay,
			int avgDurationSeconds,
			@Schema(description = "승패, 오래된→최신")
			List<Boolean> results) {
	}

	public record Champion(String championName, String championImageUrl, int games, int wins, double kda) {
	}

	public record Game(
			String gameId,
			String championName,
			String championImageUrl,
			boolean win,
			int kills,
			int deaths,
			int assists,
			Integer durationSeconds,
			OffsetDateTime endedAt,
			long cheerTotal) {
	}
}
