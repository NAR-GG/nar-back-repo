package com.toy.nar.app.auth.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청")
public record ProfileUpdateRequest(
		@Schema(description = "이름. '#'은 포함할 수 없다. 이름#태그 조합이 본인의 현재 값과 같으면 통과, 그 외에는 중복 불가",
				example = "짱아깨비")
		@NotBlank
		@Size(max = 50)
		@Pattern(regexp = "^[^#]+$", message = "이름에는 '#'을 포함할 수 없습니다")
		String name,
		@Schema(description = "태그. 영문/숫자 2~5자 (롤처럼 자유롭게 커스텀)", example = "KR2")
		@NotBlank
		@Pattern(regexp = "^[A-Za-z0-9]{2,5}$", message = "태그는 영문 또는 숫자 2~5자여야 합니다")
		String tag,
		@Schema(description = "선호 팀 ID", example = "3")
		@NotNull Long favoriteTeamId,
		@Schema(description = "프로필 이미지 URL. 선택값(nullable). 전달되면 그대로 저장한다.",
				example = "https://storage.example.com/profiles/12.png", nullable = true)
		String profileImageUrl) {
}
