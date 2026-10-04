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
			String name,
			@Schema(description = "홈 순위표 칩. null=칩 없음 / false=점선 비활성 / true=선택 가능", nullable = true)
			Boolean standings,
			@Schema(description = "경기 카드 알림 벨 노출")
			boolean alarm,
			@Schema(description = "리그 아이콘 PNG. null 이면 앱 번들 아이콘으로 폴백", nullable = true)
			String iconUrl) {
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
