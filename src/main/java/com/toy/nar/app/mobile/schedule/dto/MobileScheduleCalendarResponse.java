package com.toy.nar.app.mobile.schedule.dto;

import java.util.List;

public record MobileScheduleCalendarResponse(
		String month,
		String league,
		Long teamId,
		List<DateSummary> dates) {

	public record DateSummary(
			String date,
			long matchCount,
			List<CalendarMatch> matches) {
	}

	public record CalendarMatch(
			String matchId,
			String blueTeamCode,
			String redTeamCode,
			String blueTeamName,
			String redTeamName,
			String displayText) {
	}
}
