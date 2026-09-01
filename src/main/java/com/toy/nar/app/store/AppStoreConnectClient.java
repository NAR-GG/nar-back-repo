package com.toy.nar.app.store;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * App Store Connect API 클라이언트 — 지금은 고객 리뷰 조회 하나뿐이다.
 *
 * <p>리뷰는 폴링밖에 방법이 없다. 애플 웹훅 이벤트에 리뷰/평점이 없어서(빌드·버전 상태·에셋팩·
 * TestFlight 피드백 5종뿐) 배포 알림처럼 밀어 받을 수가 없다. 배포 알림은
 * {@code AppStoreWebhookController} 가 웹훅으로 받는다.
 *
 * <p><b>APNs 키로는 호출되지 않는다.</b> 같은 ES256 {@code .p8} 모양이지만 발급처가 다르다 —
 * App Store Connect → 사용자 및 접근 → 통합 에서 따로 받아야 하고, JWT 의 {@code aud} 도
 * {@code appstoreconnect-v1} 이다({@code ApnsLiveActivityClient} 는 aud 가 없다).
 *
 * <p>ponytail: 전용 SDK 를 붙이지 않는다. 30분에 GET 한 번이라 WebClient + jjwt 로 충분하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppStoreConnectClient {

	/** 최신순으로 받는다. 응답에서 페이지를 더 넘기지 않으므로 limit 이 곧 1회 조회 상한이다. */
	private static final String REVIEWS_URL =
			"https://api.appstoreconnect.apple.com/v1/apps/{appId}/customerReviews?sort=-createdDate&limit={limit}";

	/** ASC 는 토큰 수명을 20분으로 제한한다(넘으면 401). 만료 직전이 아니라 여유를 두고 새로 만든다. */
	private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

	private final WebClient webClient;

	/** ASC API 키 {@code .p8} 파일 원문을 base64 로 감싼 값. 파일 마운트를 늘리지 않으려고 env 로 받는다. */
	@Value("${app-store.connect.key-base64:}")
	private String keyBase64;

	@Value("${app-store.connect.key-id:}")
	private String keyId;

	/** 팀 단위 issuer ID. 키 id 와 다른 값이다 — 통합 페이지 상단에 따로 표시된다. */
	@Value("${app-store.connect.issuer-id:}")
	private String issuerId;

	/** 앱의 숫자 id(번들 id 가 아니다). App Store Connect 앱 URL 의 {@code /apps/<여기>} 부분. */
	@Value("${app-store.app-id:}")
	private String appId;

	private volatile PrivateKey privateKey;
	private volatile String cachedJwt;
	private volatile Instant cachedJwtAt;

	/** 넷 중 하나라도 비면 전 구간을 건너뛴다 — 반쯤 채운 설정으로 401 을 반복하지 않는다. */
	public boolean isAvailable() {
		return !isBlank(keyBase64) && !isBlank(keyId) && !isBlank(issuerId) && !isBlank(appId);
	}

	/**
	 * 최근 리뷰를 최신순으로 가져온다. 실패하면 빈 목록 — 다음 폴링이 다시 시도한다.
	 */
	public List<CustomerReview> fetchRecentReviews(int limit) {
		JsonNode response;
		try {
			response = webClient.get()
					.uri(REVIEWS_URL, appId, limit)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken())
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (Exception e) {
			log.warn("앱스토어 리뷰 조회 실패: {}", e.getMessage());
			return List.of();
		}
		if (response == null || !response.path("data").isArray()) {
			log.warn("앱스토어 리뷰 응답에 data 배열이 없다");
			return List.of();
		}

		List<CustomerReview> reviews = new ArrayList<>();
		for (JsonNode node : response.path("data")) {
			JsonNode attributes = node.path("attributes");
			reviews.add(new CustomerReview(
					node.path("id").asText(),
					attributes.path("rating").asInt(0),
					attributes.path("title").asText(""),
					attributes.path("body").asText(""),
					attributes.path("reviewerNickname").asText(""),
					attributes.path("territory").asText(""),
					attributes.path("createdDate").asText("")));
		}
		return reviews;
	}

	/** ASC 인증 JWT. 발급이 잦으면 거부되므로 TTL 동안 재사용한다. */
	private synchronized String authToken() {
		Instant issuedAt = cachedJwtAt;
		if (cachedJwt != null && issuedAt != null && issuedAt.plus(TOKEN_TTL).isAfter(Instant.now())) {
			return cachedJwt;
		}
		Instant now = Instant.now();
		cachedJwt = Jwts.builder()
				.header().add("kid", keyId).and()
				.issuer(issuerId)
				.audience().add("appstoreconnect-v1").and()
				.issuedAt(Date.from(now))
				// exp 는 필수다. 없거나 20분을 넘기면 ASC 가 401 로 거절한다.
				.expiration(Date.from(now.plus(TOKEN_TTL).plusSeconds(60)))
				.signWith(loadPrivateKey(), Jwts.SIG.ES256)
				.compact();
		cachedJwtAt = now;
		return cachedJwt;
	}

	private PrivateKey loadPrivateKey() {
		PrivateKey cached = privateKey;
		if (cached != null) {
			return cached;
		}
		try {
			// env 에는 .p8 파일 원문이 base64 로 들어 있다. 벗기면 PEM, 거기서 다시 벗기면 DER.
			// MIME 디코더를 쓴다 — base64 를 줄바꿈으로 접어 넣어도(도구마다 다르다) 통과한다.
			String pem = new String(Base64.getMimeDecoder().decode(keyBase64.trim()))
					.replace("-----BEGIN PRIVATE KEY-----", "")
					.replace("-----END PRIVATE KEY-----", "")
					.replaceAll("\\s", "");
			privateKey = KeyFactory.getInstance("EC")
					.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
			return privateKey;
		} catch (Exception e) {
			throw new IllegalStateException("App Store Connect .p8 키를 읽지 못했습니다 (APP_STORE_CONNECT_KEY_BASE64)", e);
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/** ASC {@code customerReviews} 한 건. */
	public record CustomerReview(
			String id,
			int rating,
			String title,
			String body,
			String nickname,
			String territory,
			String createdDate) {
	}
}
