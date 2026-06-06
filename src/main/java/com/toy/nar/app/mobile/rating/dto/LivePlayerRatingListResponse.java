package com.toy.nar.app.mobile.rating.dto;

import java.util.List;

public record LivePlayerRatingListResponse(
		String gameId,
		boolean rateable,
		List<TeamRatingSummary> teams,
		List<PlayerRatingSummary> players) {

	public record TeamRatingSummary(
			String teamSide,
			String teamName,
			double averageRating,
			long ratingCount) {
	}

	public record PlayerRatingSummary(
			Integer participantId,
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamSide,
			String role,
			String championName,
			double averageRating,
			long ratingCount,
			Integer myRating) {
	}
}
