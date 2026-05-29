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
			List<String> leagues,

			@Schema(description = "캘린더 칸 표시용 매치업 목록")
			List<CalendarMatchDto> matches) {
	}

	@Schema(description = "캘린더 칸 표시용 매치업")
	public record CalendarMatchDto(
			@Schema(description = "매치 ID", example = "115654899804988513")
			String matchId,

			@Schema(description = "블루/좌측 팀 코드", example = "HLE")
			String blueTeamCode,

			@Schema(description = "레드/우측 팀 코드", example = "BRO")
			String redTeamCode,

			@Schema(description = "블루/좌측 팀명", example = "Hanwha Life Esports")
			String blueTeamName,

			@Schema(description = "레드/우측 팀명", example = "Hanjin Brion")
			String redTeamName,

			@Schema(description = "캘린더 표시 문자열", example = "HLE vs BRO")
			String displayText) {
	}
}
