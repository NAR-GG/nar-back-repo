package com.toy.nar.app.mobile.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "선수 페이지 상단 — 프로필·지금 솔랭·마지막 솔랭·다음 경기·지표 줄")
public record PlayerPageResponse(
		Long playerId,
		String playerName,
		String playerImageUrl,
		String role,
		Long teamId,
		String teamCode,
		String teamName,
		String teamImageUrl,
		@Schema(description = "내 구독 여부. 비회원은 false")
		boolean subscribed,
		boolean startEnabled,
		boolean endEnabled,
		@Schema(description = "지금 솔랭 중인 판. 아니면 null")
		Live live,
		@Schema(description = "마지막으로 끝난 솔랭 1판(결과 수집된 판만). 없으면 null")
		LastGame lastGame,
		@Schema(description = "소속 팀의 다음 경기. 팀이 없거나 예정 경기가 없으면 null")
		NextMatch nextMatch,
		Stats stats) {

	public record Live(
			String championName,
			String championImageUrl,
			@Schema(example = "2026-10-25T21:03:12+09:00")
			OffsetDateTime startedAt,
			@Schema(example = "NA1")
			String platform,
			long cheerTotal,
			long cheerMine) {
	}

	public record LastGame(
			String championName,
			String championImageUrl,
			Boolean win,
			Integer kills,
			Integer deaths,
			Integer assists,
			Integer durationSeconds,
			OffsetDateTime endedAt) {
	}

	public record NextMatch(
			String matchId,
			String leagueName,
			@Schema(description = "표시용 스테이지", example = "스위스 4라운드")
			String stage,
			OffsetDateTime scheduledAt,
			Integer bestOf,
			MatchTeam team,
			MatchTeam opponent,
			@Schema(description = "이 경기의 의미. 없으면 null", example = "이기면 8강 진출")
			String stakeLabel) {
	}

	public record MatchTeam(
			String teamCode,
			String teamName,
			String teamImageUrl,
			@Schema(description = "이 대회 스위스 전적. 스위스가 아니면 null")
			Integer wins,
			Integer losses) {
	}

	public record Stats(
			long subscriberCount,
			@Schema(description = "최근 7일 이 선수가 받은 솔랭 응원 합")
			long cheers7d,
			@Schema(description = "이번 월즈 팬 평점. 없으면 null")
			Rating worldsRating) {
	}

	public record Rating(double average, long count) {
	}
}
