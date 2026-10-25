package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MobileScheduleCalendarResponse(
		String month,
		String league,
		Long teamId,
		List<DateSummary> dates) {

	public record DateSummary(
			@Schema(description = "경기 일자(utcOffset 기준, 기본 KST)", example = "2026-04-01")
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
