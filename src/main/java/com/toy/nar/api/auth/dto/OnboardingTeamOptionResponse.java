package com.toy.nar.api.auth.dto;

import com.toy.nar.domain.participant.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 팀 선택 옵션")
public record OnboardingTeamOptionResponse(
		@Schema(description = "팀 ID", example = "1")
		Long id,
		@Schema(description = "팀 이름", example = "T1")
		String name,
		@Schema(description = "팀 코드", example = "T1")
		String code,
		@Schema(description = "팀 로고 이미지 URL", example = "https://static.example.com/t1.png", nullable = true)
		String imageUrl
) {

	public static OnboardingTeamOptionResponse from(Team team) {
		return new OnboardingTeamOptionResponse(
				team.getId(),
				team.getName(),
				team.getCode(),
				team.getImageUrl()
		);
	}
}
