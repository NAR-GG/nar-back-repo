package com.toy.nar.app.auth.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청")
public record ProfileUpdateRequest(
		@Schema(description = "닉네임. 본인의 현재 닉네임과 동일하면 통과, 그 외에는 중복 불가", example = "용맹한바론")
		@NotBlank
		@Size(max = 50)
		String nickname,
		@Schema(description = "선호 팀 ID", example = "3")
		@NotNull Long favoriteTeamId,
		@Schema(description = "프로필 이미지 URL. 선택값(nullable). 전달되면 그대로 저장한다.",
				example = "https://storage.example.com/profiles/12.png", nullable = true)
		String profileImageUrl) {
}
