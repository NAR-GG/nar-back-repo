package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MobileScheduleListResponse(
		String date,
		String league,
		Long teamId,
		List<MobileMatchSummary> matches) {

	public record MobileMatchSummary(
			String matchId,
			@Schema(description = "경기 일자(KST)", example = "2026-04-01")
			String date,
			String scheduledTime,
			String matchStatus,
			String matchTitle,
			String leagueName,
			MobileTeamResult blueTeam,
			MobileTeamResult redTeam,
			String liveStreamUrl,
			@Schema(description = "매치에 속한 세트(게임) 목록. 아직 세트가 생성되지 않은 매치는 빈 배열")
			List<MobileGameSummary> games) {
	}

	public record MobileGameSummary(
			@Schema(description = "세트 순서(1부터 시작)", example = "1")
			Integer gameOrder,
			@Schema(description = "라이브/선수 평점 API에서 사용하는 esports gameId", example = "113990000000000001")
			String gameId,
			@Schema(description = "기록(record) API에서 사용하는 내부 gameId. 기록 미적재 시 null", example = "1024", nullable = true)
			Long recordGameId) {
	}

	public record MobileTeamResult(
			String teamName,
			String teamCode,
			String teamImageUrl,
			int score) {
	}
}
