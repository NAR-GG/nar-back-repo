package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "모바일 카카오 로그인 요청")
public record KakaoMobileLoginRequest(
		@NotBlank
		@Schema(description = "Flutter Kakao SDK에서 발급받은 Kakao Access Token", requiredMode = Schema.RequiredMode.REQUIRED)
		String accessToken) {
}
