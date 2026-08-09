package com.toy.nar.app.mobile.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * iOS Live Activity 갱신용 APNs HTTP/2 클라이언트.
 *
 * <p>FCM 을 못 쓰는 유일한 경로다 — ActivityKit 푸시 토큰은 FCM 등록 토큰이 아니라서
 * firebase-admin 으로 보낼 수 없다. 대신 발송량이 작아(세트/스코어가 바뀔 때만이라 경기당 10~20건)
 * 전용 SDK 없이 JDK HttpClient + jjwt 로 충분하다.
 *
 * <p>ponytail: 커넥션 풀·재시도·배치를 직접 하지 않는다. HttpClient 가 HTTP/2 멀티플렉싱을
 * 알아서 하고 발송량이 작아 지금은 이득이 없다. 발송량이 커지거나 부분 실패 재시도가
 * 필요해지면 pushy(com.eatthepath:pushy)로 갈아탄다.
 */
@Slf4j
@Component
public class ApnsLiveActivityClient {

	/** APNs 는 인증 토큰 수명을 1시간으로 제한한다. 만료 직전이 아니라 여유를 두고 새로 만든다. */
	private static final Duration TOKEN_TTL = Duration.ofMinutes(50);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Value("${apns.enabled:false}")
	private boolean enabled;

	/** Apple Developer 에서 받은 .p8 키 파일 경로. */
	@Value("${apns.key-path:}")
	private String keyPath;

	@Value("${apns.key-id:}")
	private String keyId;

	@Value("${apns.team-id:}")
	private String teamId;

	/** 앱 번들 id. apns-topic 은 여기에 .push-type.liveactivity 를 붙인 값이다. */
	@Value("${apns.bundle-id:}")
	private String bundleId;

	/** 개발 빌드는 sandbox 로 보내야 한다. */
	@Value("${apns.host:https://api.push.apple.com}")
	private String host;

	private volatile PrivateKey privateKey;
	private volatile String cachedJwt;
	private volatile Instant cachedJwtAt;

	public boolean isAvailable() {
		return enabled
				&& !keyPath.isBlank()
				&& !keyId.isBlank()
				&& !teamId.isBlank()
				&& !bundleId.isBlank();
	}

	/**
	 * 액티비티 하나를 갱신한다.
	 *
	 * @return 토큰이 죽었으면(410 Unregistered / 400 BadDeviceToken) false — 호출측이 비활성화한다.
	 *         그 외 실패는 예외로 던지지 않고 true 를 돌려 다음 이벤트에 다시 시도하게 둔다.
	 */
	public boolean sendUpdate(String pushToken, Map<String, Object> contentState) {
		return sendUpdateAsync(pushToken, contentState).join();
	}

	/**
	 * 액티비티를 종료한다. {@code dismissAfter} 뒤에 시스템이 카드를 내린다.
	 * 앱의 30분 자동 dismiss 타이머를 대신하므로 앱이 안 떠 있어도 카드가 남지 않는다.
	 */
	public boolean sendEnd(String pushToken, Map<String, Object> contentState, Duration dismissAfter) {
		return sendEndAsync(pushToken, contentState, dismissAfter).join();
	}

	/**
	 * 비동기 발송. 카드가 많은 경기에서는 반드시 이쪽을 써서 한꺼번에 띄워야 한다.
	 *
	 * <p>동기 발송을 토큰 수만큼 반복하면 왕복이 직렬로 쌓인다. FCM 쪽에서 같은 모양으로
	 * 사고가 났다 — 2026-07-29 LCK T1 vs KT 에서 구독자 1,500명 팬아웃이 이벤트당 8~18분
	 * 걸려 마지막 사람은 세트가 끝난 뒤에 시작 알림을 받았다({@code TeamLiveEventPushService} 참고).
	 * HTTP/2 는 한 커넥션에 요청을 다중화하므로 비동기로 넘기면 왕복이 겹쳐 전체가 한 번의
	 * 왕복 시간에 수렴한다.</p>
	 */
	public CompletableFuture<Boolean> sendUpdateAsync(String pushToken, Map<String, Object> contentState) {
		return sendAsync(pushToken, body("update", contentState, null));
	}

	public CompletableFuture<Boolean> sendEndAsync(
			String pushToken, Map<String, Object> contentState, Duration dismissAfter) {
		return sendAsync(pushToken, body("end", contentState, Instant.now().plus(dismissAfter)));
	}

	/**
	 * 카드를 새로 만든다(push-to-start, iOS 17.2+).
	 *
	 * <p>갱신과 달리 정적 속성까지 실어 보낸다. {@code attributes-type} 은 앱의
	 * ActivityAttributes 타입 이름과 정확히 같아야 하고, {@code attributes} 는 그 타입의
	 * 필드 구성과 일치해야 한다. 어긋나면 APNs 는 200 을 주고 카드만 안 뜬다.</p>
	 *
	 * <p>토큰도 다르다 — 여기 넘기는 것은 액티비티 토큰이 아니라 앱 단위
	 * push-to-start 토큰이다.</p>
	 *
	 * <p>{@code alert} 는 장식이 아니라 필수다. 없으면 iOS 가 start 이벤트를 그대로 버린다 —
	 * 실측 2026-08-09 KT vs DK: APNs 200, 기기 도착, 예산 통과까지 다 되고 liveactivitiesd 가
	 * "Received start without an alert configuration" ERROR 로 카드 생성을 중단했다.</p>
	 */
	public CompletableFuture<Boolean> sendStartAsync(
			String pushToStartToken,
			String attributesType,
			Map<String, Object> attributes,
			Map<String, Object> contentState,
			String alertTitle,
			String alertBody) {
		Map<String, Object> alert = new LinkedHashMap<>();
		alert.put("title", alertTitle);
		alert.put("body", alertBody);
		Map<String, Object> aps = new LinkedHashMap<>();
		aps.put("timestamp", Instant.now().getEpochSecond());
		aps.put("event", "start");
		aps.put("attributes-type", attributesType);
		aps.put("attributes", attributes);
		aps.put("content-state", contentState);
		aps.put("alert", alert);
		return sendAsync(pushToStartToken, Map.of("aps", aps));
	}

	private Map<String, Object> body(String event, Map<String, Object> contentState, Instant dismissalDate) {
		Map<String, Object> aps = new LinkedHashMap<>();
		aps.put("timestamp", Instant.now().getEpochSecond());
		aps.put("event", event);
		aps.put("content-state", contentState);
		if (dismissalDate != null) {
			aps.put("dismissal-date", dismissalDate.getEpochSecond());
		}
		return Map.of("aps", aps);
	}

	/**
	 * @return 토큰이 살아 있으면 true. 실패해도 예외를 흘리지 않는다 —
	 *         일시 오류는 true 로 두어 다음 이벤트에 자연히 재시도된다.
	 */
	private CompletableFuture<Boolean> sendAsync(String pushToken, Map<String, Object> payload) {
		if (!isAvailable()) {
			return CompletableFuture.completedFuture(true);
		}
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(host + "/3/device/" + pushToken))
					.header("authorization", "bearer " + authToken())
					.header("apns-topic", bundleId + ".push-type.liveactivity")
					.header("apns-push-type", "liveactivity")
					.header("apns-priority", "10")
					.header("apns-expiration", "0")
					.timeout(Duration.ofSeconds(10))
					.POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
					.build();
		} catch (Exception e) {
			// 키 로딩·JSON 직렬화 실패. 토큰 문제가 아니므로 살려 둔다.
			log.warn("APNs Live Activity 요청 생성 실패: {}", e.getMessage());
			return CompletableFuture.completedFuture(true);
		}

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.handle((response, error) -> {
					if (error != null) {
						log.warn("APNs Live Activity 발송 오류: {}", error.getMessage());
						return true;
					}
					if (response.statusCode() == 200) {
						return true;
					}
					// 410 = 액티비티가 끝났거나 앱이 지워져 토큰이 죽었다. 400 BadDeviceToken 도 재사용 불가.
					boolean tokenDead = response.statusCode() == 410
							|| (response.statusCode() == 400 && response.body().contains("BadDeviceToken"));
					log.warn("APNs Live Activity 발송 실패 status={} body={} tokenDead={}",
							response.statusCode(), response.body(), tokenDead);
					return !tokenDead;
				});
	}

	/** APNs 인증 JWT. 발급이 잦으면 APNs 가 거부하므로 TTL 동안 재사용한다. */
	private synchronized String authToken() throws IOException {
		Instant issuedAt = cachedJwtAt;
		if (cachedJwt != null && issuedAt != null && issuedAt.plus(TOKEN_TTL).isAfter(Instant.now())) {
			return cachedJwt;
		}
		cachedJwt = Jwts.builder()
				.header().add("kid", keyId).and()
				.issuer(teamId)
				.issuedAt(new Date())
				.signWith(loadPrivateKey(), Jwts.SIG.ES256)
				.compact();
		cachedJwtAt = Instant.now();
		return cachedJwt;
	}

	private PrivateKey loadPrivateKey() throws IOException {
		PrivateKey cached = privateKey;
		if (cached != null) {
			return cached;
		}
		String pem = Files.readString(Path.of(keyPath))
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			privateKey = KeyFactory.getInstance("EC")
					.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
			return privateKey;
		} catch (Exception e) {
			throw new IOException("APNs .p8 키를 읽지 못했습니다: " + keyPath, e);
		}
	}
}
