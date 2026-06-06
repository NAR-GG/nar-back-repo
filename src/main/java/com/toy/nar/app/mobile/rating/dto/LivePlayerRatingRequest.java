package com.toy.nar.app.mobile.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LivePlayerRatingRequest(
		@NotNull
		@Min(1)
		@Max(5)
		Integer rating,
		@Size(max = 150)
		String comment) {
}
