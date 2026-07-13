package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "모바일 Apple 로그인 요청")
public record AppleMobileLoginRequest(
		@NotBlank
		@Schema(description = "Sign in with Apple에서 발급받은 identity token(JWT)", requiredMode = Schema.RequiredMode.REQUIRED)
		String idToken) {
}
