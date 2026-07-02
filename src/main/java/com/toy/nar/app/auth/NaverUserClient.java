package com.toy.nar.app.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.domain.member.entity.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class NaverUserClient {

	private static final String NAVER_USER_ME_URL = "https://openapi.naver.com/v1/nid/me";

	private final WebClient webClient;

	public SocialAccountInfo fetchUser(String naverAccessToken) {
		if (!StringUtils.hasText(naverAccessToken)) {
			throw new ResponseStatusException(UNAUTHORIZED, "네이버 액세스 토큰이 필요합니다");
		}

		JsonNode body = requestUserInfo(naverAccessToken);
		if (body == null) {
			throw new ResponseStatusException(BAD_GATEWAY, "네이버 사용자 정보 응답이 비어 있습니다");
		}

		// 네이버는 프로필을 response 하위에 담고, resultcode "00"이 성공이다.
		String resultCode = body.path("resultcode").asText(null);
		if (!"00".equals(resultCode)) {
			throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 네이버 액세스 토큰");
		}

		JsonNode response = body.path("response");
		String providerId = response.path("id").asText(null);
		if (!StringUtils.hasText(providerId)) {
			throw new ResponseStatusException(UNAUTHORIZED, "네이버 사용자 정보를 확인할 수 없습니다");
		}

		String email = response.path("email").asText(null);
		if (!StringUtils.hasText(email)) {
			email = null;
		}

		return new SocialAccountInfo(OAuthProvider.NAVER, providerId, email);
	}

	private JsonNode requestUserInfo(String naverAccessToken) {
		try {
			return webClient.get()
					.uri(NAVER_USER_ME_URL)
					.headers(headers -> headers.setBearerAuth(naverAccessToken))
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (WebClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			if (status.is4xxClientError()) {
				throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 네이버 액세스 토큰", e);
			}
			throw new ResponseStatusException(BAD_GATEWAY, "네이버 사용자 정보 조회에 실패했습니다", e);
		}
	}
}
