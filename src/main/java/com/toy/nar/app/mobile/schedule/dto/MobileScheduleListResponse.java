package com.toy.nar.app.mobile.schedule.dto;

import java.util.List;

public record MobileScheduleListResponse(
		String date,
		String league,
		Long teamId,
		List<MobileMatchSummary> matches) {

	public record MobileMatchSummary(
			String matchId,
			String scheduledTime,
			String matchStatus,
			String matchTitle,
			String leagueName,
			MobileTeamResult blueTeam,
			MobileTeamResult redTeam,
			String liveStreamUrl) {
	}

	public record MobileTeamResult(
			String teamName,
			String teamCode,
			String teamImageUrl,
			int score) {
	}
}
