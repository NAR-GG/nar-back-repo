package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.OAuthProvider;

public record SocialAccountInfo(
		OAuthProvider provider,
		String providerId,
		String email) {
}
