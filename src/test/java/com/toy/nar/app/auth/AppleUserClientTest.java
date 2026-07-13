package com.toy.nar.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.domain.member.entity.OAuthProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleUserClientTest {

	private static final String BUNDLE_ID = "com.nar.wardingapp";

	private final KeyPair keyPair = generateKeyPair();

	/** 실제 애플 대신 로컬 키페어로 서명·검증한다. fetchJwksJson 만 가짜로 대체. */
	private AppleUserClient clientWithLocalJwks() {
		return new AppleUserClient(null, BUNDLE_ID) {
			@Override
			protected String fetchJwksJson() {
				// Jwk 는 Map 이라 Jackson 으로 바로 JWKS JSON 이 된다.
				var jwk = Jwks.builder().key((RSAPublicKey) keyPair.getPublic()).id("test-kid").build();
				try {
					return new ObjectMapper().writeValueAsString(
							java.util.Map.of("keys", java.util.List.of(jwk)));
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
		};
	}

	private String identityToken(String issuer, String audience, String subject) {
		return Jwts.builder()
				.header().keyId("test-kid").and()
				.issuer(issuer)
				.audience().add(audience).and()
				.subject(subject)
				.claim("email", "apple-user@privaterelay.appleid.com")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(keyPair.getPrivate())
				.compact();
	}

	@Test
	void validIdentityTokenReturnsAppleAccountInfo() {
		AppleUserClient client = clientWithLocalJwks();

		SocialAccountInfo info = client.fetchUser(
				identityToken("https://appleid.apple.com", BUNDLE_ID, "apple-sub-001"));

		assertThat(info.provider()).isEqualTo(OAuthProvider.APPLE);
		assertThat(info.providerId()).isEqualTo("apple-sub-001");
		assertThat(info.email()).isEqualTo("apple-user@privaterelay.appleid.com");
	}

	@Test
	void wrongIssuerRejected() {
		AppleUserClient client = clientWithLocalJwks();

		assertThatThrownBy(() -> client.fetchUser(
				identityToken("https://evil.example.com", BUNDLE_ID, "apple-sub-001")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401");
	}

	@Test
	void wrongAudienceRejected() {
		AppleUserClient client = clientWithLocalJwks();

		assertThatThrownBy(() -> client.fetchUser(
				identityToken("https://appleid.apple.com", "com.other.app", "apple-sub-001")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401");
	}

	@Test
	void tamperedSignatureRejected() {
		AppleUserClient client = clientWithLocalJwks();
		String token = identityToken("https://appleid.apple.com", BUNDLE_ID, "apple-sub-001");
		String tampered = token.substring(0, token.length() - 4) + "AAAA";

		assertThatThrownBy(() -> client.fetchUser(tampered))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401");
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
