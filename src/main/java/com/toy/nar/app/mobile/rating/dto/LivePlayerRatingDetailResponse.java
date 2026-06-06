package com.toy.nar.app.mobile.rating.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LivePlayerRatingDetailResponse(
		String gameId,
		boolean rateable,
		PlayerHeader player,
		double averageRating,
		long ratingCount,
		List<RatingDistribution> distribution,
		MyRating myRating,
		List<Review> reviews,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public record PlayerHeader(
			Integer participantId,
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamSide,
			String role,
			String championName,
			Integer kills,
			Integer deaths,
			Integer assists) {
	}

	public record RatingDistribution(int rating, long count, double percentage) {
	}

	public record MyRating(Long ratingId, int rating, String comment) {
	}

	public record Review(
			Long ratingId,
			String nickname,
			int rating,
			String comment,
			boolean mine,
			LocalDateTime createdAt,
			LocalDateTime updatedAt) {
	}
}
