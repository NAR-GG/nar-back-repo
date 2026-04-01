package com.toy.nar.app.schedule.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "월별 캘린더 경기 일정 응답")
public record ScheduleCalendarResponseDto(
		@Schema(description = "조회 월 (yyyy-MM)", example = "2026-04")
		String month,
		
		@Schema(description = "일별 경기 일정 요약 목록")
		List<ScheduleDateSummaryDto> dates) {

	@Schema(description = "특정 날짜의 경기 일정 요약")
	public record ScheduleDateSummaryDto(
			@Schema(description = "날짜 (yyyy-MM-dd)", example = "2026-04-01")
			String date,
			
			@Schema(description = "해당 날짜의 총 경기 수", example = "3")
			long matchCount,
			
			@Schema(description = "해당 날짜에 경기가 있는 리그 이름 목록", example = "[\"LCK\", \"LPL\"]")
			List<String> leagues) {
	}
}
