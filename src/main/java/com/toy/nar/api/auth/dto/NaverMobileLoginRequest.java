package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "모바일 Naver 로그인 요청")
public record NaverMobileLoginRequest(
		@NotBlank
		@Schema(description = "Naver Login SDK에서 발급받은 Access Token", requiredMode = Schema.RequiredMode.REQUIRED)
		String accessToken) {
}
