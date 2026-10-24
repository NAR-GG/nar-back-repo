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
			@Schema(description = "스테이지. 스위스면 이 선수 팀 기준 라운드. 평점 없는 세트는 모름(null)", example = "스위스 3라운드")
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

	public record Review(
			long ratingId,
			@Schema(description = "라이브 게임 id. 평점 상세(/api/mobile/live/games/{gameId}/participants/{participantId}/ratings)로 간다")
			String gameId,
			int participantId,
			@Schema(description = "그 세트 진영", example = "Blue")
			String teamSide,
			int rating,
			String comment,
			String opponentTeamCode,
			@Schema(example = "스위스 3라운드")
			String stage,
			OffsetDateTime createdAt) {
	}

	@Schema(description = "한줄평 전체 보기 — 최신순 페이지")
	public record ReviewPage(List<Review> reviews, long totalCount, boolean hasNext) {
	}
}
