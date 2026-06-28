package com.toy.nar.app.mobile.rating.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내가 작성한 선수 평가 전체 목록 응답")
public record MyRatingListResponse(
		List<MyRatingItem> ratings,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public record MyRatingItem(
			Long ratingId,
			@Schema(description = "평가 대상 세트의 esports gameId", example = "113990000000000001")
			String gameId,
			Integer participantId,
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamSide,
			String role,
			String championName,
			int rating,
			String comment,
			LocalDateTime createdAt,
			LocalDateTime updatedAt,
			@Schema(description = "작성자(나) 프로필 이미지 URL. 없으면 null", nullable = true)
			String profileImageUrl,
			@Schema(description = "작성자(나) 응원팀 로고 URL. 없으면 null", nullable = true)
			String teamImageUrl,
			@Schema(description = "세트가 속한 매치 정보. 매핑이 없으면 null", nullable = true)
			MatchInfo match) {
	}

	public record MatchInfo(
			String matchId,
			@Schema(description = "세트 순서(1부터 시작)", example = "2")
			Integer gameOrder,
			String leagueName,
			String matchTitle,
			String blueTeamCode,
			String redTeamCode,
			@Schema(description = "경기 일시(KST)", example = "2026-04-01T18:00:00")
			LocalDateTime matchDate) {
	}
}
