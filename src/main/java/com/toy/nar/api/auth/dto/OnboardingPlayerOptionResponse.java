package com.toy.nar.api.auth.dto;

import com.toy.nar.domain.participant.entity.Player;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 선수 선택 옵션")
public record OnboardingPlayerOptionResponse(
        @Schema(description = "선수 ID", example = "10")
        Long id,
        @Schema(description = "선수명", example = "Faker")
        String name,
        @Schema(description = "선수 이미지 URL", nullable = true)
        String imageUrl,
        @Schema(description = "포지션", example = "mid", nullable = true)
        String role
) {

    public static OnboardingPlayerOptionResponse from(Player player) {
        return new OnboardingPlayerOptionResponse(
                player.getId(),
                player.getName(),
                player.getImageUrl(),
                player.getRole()
        );
    }

    public static OnboardingPlayerOptionResponse from(
            com.toy.nar.domain.participant.repository.PlayerRepository.LckPlayerOption option) {
        return new OnboardingPlayerOptionResponse(
                option.getPlayerId(),
                option.getPlayerName(),
                option.getPlayerImageUrl(),
                option.getRole()
        );
    }
}
