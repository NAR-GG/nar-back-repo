package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "모바일 Google 로그인 요청")
public record GoogleMobileLoginRequest(
		@NotBlank
		@Schema(description = "Google Sign-In SDK에서 발급받은 ID Token", requiredMode = Schema.RequiredMode.REQUIRED)
		String idToken) {
}
