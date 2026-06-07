package com.toy.nar.app.mobile.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record PlayerSubscriptionRequest(
		@NotNull Long playerId) {
}
