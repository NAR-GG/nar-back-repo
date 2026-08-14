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
		boolean hasNext,
		@Schema(description = "과거 방향 조회용 커서(before 파라미터로 되던진다). 더 과거가 없으면 null",
				nullable = true)
		String prevCursor,
		@Schema(description = "목록 앞쪽(과거)에 더 있는지. around/before 응답에서만 의미가 있다")
		boolean hasPrev) {

	/**
	 * 과거 방향 정보가 없는(기존) 응답. {@code around}/{@code before} 없이 부른 호출은
	 * 위쪽에 뭐가 있는지 계산하지 않으므로 {@code prevCursor}=null, {@code hasPrev}=false 다.
	 */
	public static MobileMatchPageResponse forward(
			String league,
			Long teamId,
			List<MobileScheduleListResponse.MobileMatchSummary> matches,
			String nextCursor) {
		return new MobileMatchPageResponse(league, teamId, matches, nextCursor, nextCursor != null, null, false);
	}
}
