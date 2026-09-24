package com.toy.nar.app.mobile.rating.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "모든 경기의 최근 한줄평 목록 응답 (한줄평이 달린 평가만, 최신순)")
public record RecentRatingListResponse(
		List<MyRatingListResponse.MyRatingItem> ratings,
		@Schema(description = "다음 페이지 커서(마지막 ratingId). 더 없으면 null", nullable = true)
		Long nextCursor) {
}
