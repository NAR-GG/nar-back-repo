package com.toy.nar.app.auth.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cloudinary 프로필 이미지 서명 업로드 파라미터. 앱은 이 값으로 Cloudinary에 직접 업로드한다.")
public record ProfileImageUploadSignatureResponse(
		@Schema(description = "Cloudinary cloud name", example = "dvvurdffw")
		String cloudName,
		@Schema(description = "Cloudinary API key", example = "422645889216881")
		String apiKey,
		@Schema(description = "서명에 사용된 Unix timestamp(초)", example = "1700000000")
		long timestamp,
		@Schema(description = "업로드 대상 public_id (회원별 고정)", example = "profiles/2")
		String publicId,
		@Schema(description = "기존 이미지 덮어쓰기 여부", example = "true")
		boolean overwrite,
		@Schema(description = "HMAC-SHA1 업로드 서명")
		String signature,
		@Schema(description = "Cloudinary 업로드 엔드포인트 URL",
				example = "https://api.cloudinary.com/v1_1/dvvurdffw/image/upload")
		String uploadUrl) {
}
