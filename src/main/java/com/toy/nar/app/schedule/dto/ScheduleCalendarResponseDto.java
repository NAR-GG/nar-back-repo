package com.toy.nar.app.schedule.dto;

import java.util.List;

public record ScheduleCalendarResponseDto(
		String month,
		List<ScheduleDateSummaryDto> dates) {

	public record ScheduleDateSummaryDto(
			String date,
			long matchCount) {
	}
}
