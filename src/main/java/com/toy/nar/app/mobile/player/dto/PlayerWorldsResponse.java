package com.toy.nar.app.mobile.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "선수 페이지 월즈 탭. 경기 기록은 Oracle's Elixir CSV 라 실제 경기보다 늦게 찬다")
public record PlayerWorldsResponse(
		Record record,
		@Schema(description = "KDA·KP·DPM·CSPM·GD15·VSPM 순. 기록 없으면 빈 배열")
		List<Stat> stats,
		@Schema(description = "세트별 기록, 최신순")
		List<Game> games,
		@Schema(description = "솔랭(최근 20판)·월즈 챔피언 합집합. 정렬은 앱이 한다")
		List<Champion> champions,
		@Schema(description = "이번 월즈 팬 평점. 없으면 null")
		Rating rating) {

	public record Record(int games, int wins, int losses) {
	}

	public record Stat(
			@Schema(example = "KDA")
			String key,
			double value,
			@Schema(description = "같은 포지션 선수 평균")
			double positionAvg,
			@Schema(description = "같은 포지션 안 순위(1 = 최고)")
			int positionRank,
			int positionCount) {
	}

	public record Game(
			long gameId,
			OffsetDateTime playedAt,
			boolean win,
			@Schema(description = "스테이지(블록명). 모르면 null", example = "스위스")
			String stage,
			@Schema(description = "해외 팀은 CSV 에 코드·로고가 없기도 하다. 그때는 팀 이름으로 그린다")
			String opponentTeamCode,
			String opponentTeamName,
			String opponentTeamImageUrl,
			String championName,
			String championImageUrl,
			int kills,
			int deaths,
			int assists,
			Integer cs,
			Integer damage,
			PlayerPageResponse.Rating rating) {
	}

	public record Champion(
			String championName,
			String championImageUrl,
			int soloWins,
			int soloLosses,
			int worldsWins,
			int worldsLosses) {
	}

	public record Rating(
			double average,
			long count,
			@Schema(description = "5점→1점")
			List<Bucket> distribution,
			@Schema(description = "최신 한줄평 최대 3개")
			List<Review> reviews,
			long reviewCount) {
	}

	public record Bucket(int rating, long count, double percentage) {
	}

	public record Review(int rating, String comment, String opponentTeamCode, String stage, OffsetDateTime createdAt) {
	}
}
