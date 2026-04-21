package com.toy.nar.api.auth.dto;

import jakarta.validation.constraints.NotNull;

public record OnboardingRequest(@NotNull Long favoriteTeamId) {
}
