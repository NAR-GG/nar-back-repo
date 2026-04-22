package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "온보딩 완료 요청")
public record OnboardingRequest(
		@Schema(description = "선호 팀 ID", example = "1")
		@NotNull Long favoriteTeamId) {
}
