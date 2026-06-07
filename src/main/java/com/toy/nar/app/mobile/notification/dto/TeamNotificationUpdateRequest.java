package com.toy.nar.app.mobile.notification.dto;

import jakarta.validation.constraints.NotNull;

public record TeamNotificationUpdateRequest(
		@NotNull Boolean setStartEnabled,
		@NotNull Boolean setEndEnabled,
		@NotNull Boolean liveEventEnabled) {
}
