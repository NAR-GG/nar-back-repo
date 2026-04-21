package com.toy.nar.api.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, boolean isOnboarded) {
}
