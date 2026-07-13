package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.OAuthProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Sign in with Apple identity token(JWT) 검증.
 * 구글과 달리 애플은 토큰 검증 API가 없어 JWKS 공개키로 서명을 직접 검증한다.
 */
@Service
public class AppleUserClient {

	private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
	private static final String APPLE_ISSUER = "https://appleid.apple.com";

	private final WebClient webClient;
	private final List<String> allowedBundleIds;

	/** kid → 공개키. 애플 키 로테이션 시 miss가 나면 refreshKeys()로 갱신된다. */
	private volatile Map<String, Key> cachedKeys = Map.of();

	public AppleUserClient(WebClient webClient,
			@Value("${oauth.apple.mobile.bundle-ids:}") String bundleIds) {
		this.webClient = webClient;
		this.allowedBundleIds = Arrays.stream(bundleIds.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
	}

	public SocialAccountInfo fetchUser(String identityToken) {
		if (!StringUtils.hasText(identityToken)) {
			throw new ResponseStatusException(UNAUTHORIZED, "Apple identity token이 필요합니다");
		}

		final Claims claims;
		try {
			claims = Jwts.parser()
					.keyLocator(this::locateKey)
					.requireIssuer(APPLE_ISSUER)
					.build()
					.parseSignedClaims(identityToken)
					.getPayload();
		} catch (ResponseStatusException e) {
			throw e;
		} catch (JwtException | IllegalArgumentException e) {
			throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 Apple identity token", e);
		}

		Set<String> audiences = claims.getAudience();
		if (!allowedBundleIds.isEmpty()
				&& (audiences == null || audiences.stream().noneMatch(allowedBundleIds::contains))) {
			throw new ResponseStatusException(UNAUTHORIZED, "허용되지 않은 앱에서 발급된 Apple 토큰입니다");
		}

		String providerId = claims.getSubject();
		if (!StringUtils.hasText(providerId)) {
			throw new ResponseStatusException(UNAUTHORIZED, "Apple 사용자 정보를 확인할 수 없습니다");
		}

		String email = claims.get("email", String.class);
		if (!StringUtils.hasText(email)) {
			email = null;
		}

		return new SocialAccountInfo(OAuthProvider.APPLE, providerId, email);
	}

	private Key locateKey(Header header) {
		String kid = (String) header.get("kid");
		if (kid == null) {
			throw new ResponseStatusException(UNAUTHORIZED, "Apple 토큰 헤더에 kid가 없습니다");
		}
		Key key = cachedKeys.get(kid);
		if (key == null) {
			refreshKeys();
			key = cachedKeys.get(kid);
		}
		if (key == null) {
			throw new ResponseStatusException(UNAUTHORIZED, "Apple 공개키를 찾을 수 없습니다: " + kid);
		}
		return key;
	}

	private synchronized void refreshKeys() {
		String jwksJson = fetchJwksJson();
		if (!StringUtils.hasText(jwksJson)) {
			throw new ResponseStatusException(BAD_GATEWAY, "Apple 공개키 응답이 비어 있습니다");
		}
		JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
		Map<String, Key> keys = new HashMap<>();
		for (Jwk<?> jwk : jwkSet.getKeys()) {
			keys.put(jwk.getId(), jwk.toKey());
		}
		cachedKeys = Map.copyOf(keys);
	}

	/** 테스트에서 가짜 JWKS를 주입할 수 있도록 분리. */
	protected String fetchJwksJson() {
		try {
			return webClient.get()
					.uri(APPLE_JWKS_URL)
					.retrieve()
					.bodyToMono(String.class)
					.block();
		} catch (Exception e) {
			throw new ResponseStatusException(BAD_GATEWAY, "Apple 공개키 조회에 실패했습니다", e);
		}
	}
}
