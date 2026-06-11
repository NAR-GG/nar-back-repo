package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "모바일 경기 리스트 커서 페이지 응답")
public record MobileMatchPageResponse(
		String league,
		Long teamId,
		@Schema(description = "최신순(과거 방향) 경기 목록")
		List<MobileScheduleListResponse.MobileMatchSummary> matches,
		@Schema(description = "다음 페이지 조회용 커서. 마지막 페이지면 null", nullable = true)
		String nextCursor,
		@Schema(description = "다음 페이지 존재 여부")
		boolean hasNext) {
}
