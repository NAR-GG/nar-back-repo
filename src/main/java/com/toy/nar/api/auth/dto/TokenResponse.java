package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 또는 토큰 재발급 응답")
public record TokenResponse(
		@Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9.access-token")
		String accessToken,
		@Schema(description = "JWT Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token")
		String refreshToken,
		@Schema(description = "온보딩 완료 여부", example = "true")
		boolean isOnboarded) {
}
