package com.toy.nar.app.auth;

public record AuthTokens(
		String accessToken,
		String refreshToken,
		boolean isOnboarded) {
}
