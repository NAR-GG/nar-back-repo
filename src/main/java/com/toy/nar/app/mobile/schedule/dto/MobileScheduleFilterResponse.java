package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MobileScheduleFilterResponse(
		String defaultLeague,
		List<LeagueOption> leagues,
		List<TeamOption> teams,
		@Schema(description = "선택한 리그에서 필터로 쓸 수 있는 시즌 목록 (최신순)")
		List<SeasonOption> seasons) {

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

	public record SeasonOption(
			@Schema(description = "시즌 연도", example = "2026")
			int year,
			@Schema(description = "스플릿", example = "Spring")
			String split,
			@Schema(description = "표시용 라벨", example = "2026 Spring")
			String label) {
	}
}
