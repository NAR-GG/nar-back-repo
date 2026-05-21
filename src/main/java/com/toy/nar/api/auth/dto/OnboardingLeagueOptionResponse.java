package com.toy.nar.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 리그 선택 옵션")
public record OnboardingLeagueOptionResponse(
        @Schema(description = "리그명", example = "LCK")
        String name,
        @Schema(description = "리그 지역명", example = "대한민국")
        String regionName,
        @Schema(description = "리그 로고 이미지 URL")
        String imageUrl
) {
}
