package com.toy.nar.app.mobile.notification.dto;

import jakarta.validation.constraints.NotNull;

public record TeamNotificationSubscribeRequest(
		@NotNull Long teamId) {
}
