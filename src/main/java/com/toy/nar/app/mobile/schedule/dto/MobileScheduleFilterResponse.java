package com.toy.nar.app.mobile.schedule.dto;

import java.util.List;

public record MobileScheduleFilterResponse(
		String defaultLeague,
		List<LeagueOption> leagues,
		List<TeamOption> teams) {

	public record LeagueOption(
			String code,
			String name) {
	}

	public record TeamOption(
			Long teamId,
			String teamName,
			String teamCode,
			String teamImageUrl) {
	}
}
