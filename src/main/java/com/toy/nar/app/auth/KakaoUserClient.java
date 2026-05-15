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
public class KakaoUserClient {

	private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

	private final WebClient webClient;

	public SocialAccountInfo fetchUser(String kakaoAccessToken) {
		if (!StringUtils.hasText(kakaoAccessToken)) {
			throw new ResponseStatusException(UNAUTHORIZED, "카카오 액세스 토큰이 필요합니다");
		}

		JsonNode user = requestUserInfo(kakaoAccessToken);
		if (user == null) {
			throw new ResponseStatusException(BAD_GATEWAY, "카카오 사용자 정보 응답이 비어 있습니다");
		}

		String providerId = user.path("id").asText(null);
		if (!StringUtils.hasText(providerId)) {
			throw new ResponseStatusException(UNAUTHORIZED, "카카오 사용자 정보를 확인할 수 없습니다");
		}

		JsonNode kakaoAccount = user.path("kakao_account");
		String email = kakaoAccount.isMissingNode() ? null : kakaoAccount.path("email").asText(null);
		if (!StringUtils.hasText(email)) {
			email = null;
		}

		return new SocialAccountInfo(OAuthProvider.KAKAO, providerId, email);
	}

	private JsonNode requestUserInfo(String kakaoAccessToken) {
		try {
			return webClient.get()
					.uri(KAKAO_USER_ME_URL)
					.headers(headers -> headers.setBearerAuth(kakaoAccessToken))
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (WebClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			if (status.is4xxClientError()) {
				throw new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 카카오 액세스 토큰", e);
			}
			throw new ResponseStatusException(BAD_GATEWAY, "카카오 사용자 정보 조회에 실패했습니다", e);
		}
	}
}
