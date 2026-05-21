package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "온보딩 완료 요청")
public record OnboardingRequest(
		@Schema(description = "선호 리그명", example = "LCK")
		String favoriteLeagueName,
		@Schema(description = "선호 팀 ID", example = "1")
		@NotNull Long favoriteTeamId,
		@Schema(description = "선호 선수 ID 목록", example = "[10, 11]")
		List<Long> favoritePlayerIds) {
}
