package com.toy.nar.app.mobile.notification.dto;

public record TeamNotificationSubscriptionResponse(
		Long teamId,
		String teamCode,
		String teamName,
		String teamImageUrl,
		boolean favoriteTeam,
		boolean subscribed,
		boolean setStartEnabled,
		boolean setEndEnabled,
		boolean liveEventEnabled,
		boolean killEnabled,
		boolean baronEnabled,
		boolean dragonEnabled,
		boolean towerEnabled,
		boolean inhibitorEnabled) {
}
