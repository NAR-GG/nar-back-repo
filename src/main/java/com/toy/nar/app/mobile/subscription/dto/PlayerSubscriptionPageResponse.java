package com.toy.nar.app.mobile.subscription.dto;

import java.util.List;

public record PlayerSubscriptionPageResponse(
		List<PlayerSubscriptionResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
