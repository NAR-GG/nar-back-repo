package com.toy.nar.app.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.domain.member.entity.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class GoogleUserClient {

	// Google이 서명·만료를 검증해주므로 tokeninfo 200 응답이면 유효한 idToken.
	// aud(발급 대상)는 우리가 직접 허용 목록과 대조해야 한다.
	private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

	private final WebClient webClient;
	private final List<String> allowedAudiences;

	public GoogleUserClient(WebClient webClient,
			@Value("${oauth.google.mobile.client-ids:}") String clientIds) {
		this.webClient = webClient;
		this.allowedAudiences = Arrays.stream(clientIds.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
	}

	public SocialAccountInfo fetchUser(String idToken) {
		if (!StringUtils.hasText(idToken)) {
			throw new ResponseStatusException(UNAUTHORIZED, "Google ID 토큰이 필요합니다");
		}

		JsonNode payload = requestTokenInfo(idToken);
		if (payload == null) {
			throw new ResponseStatusException(BAD_GATEWAY, "Google 토큰 검증 응답이 비어 있습니다");
		}

		String audience = payload.path("aud").asText(null);
		if (!allowedAudiences.isEmpty() && !allowedAudiences.contains(audience)) {
			throw new ResponseStatusException(UNAUTHORIZED, "허용되지 않은 Google 클라이언트에서 발급된 토큰입니다");
		}

		String providerId = payload.path("sub").asText(null);
		if (!StringUtils.hasText(providerId)) {
			throw new ResponseStatusException(UNAUTHORIZED, "Google 사용자 정보를 확인할 수 없습니다");
		}

		String email = payload.path("email").asText(null);
		if (!StringUtils.hasText(email)) {
			email = null;
		}

		return new SocialAccountInfo(OAuthProvider.GOOGLE, providerId, email);
	}

	private JsonNode requestTokenInfo(String idToken) {
		String uri = UriComponentsBuilder.fromHttpUrl(GOOGLE_TOKENINFO_URL)
				.queryParam("id_token", idToken)
				.toUriString();
		try {
			return webClient.get()
					.uri(uri)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (WebClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			if (status.is4xxClientError()) {
				throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 Google ID 토큰", e);
			}
			throw new ResponseStatusException(BAD_GATEWAY, "Google 토큰 검증에 실패했습니다", e);
		}
	}
}
