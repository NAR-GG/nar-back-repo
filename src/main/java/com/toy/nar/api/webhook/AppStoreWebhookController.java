package com.toy.nar.api.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.data.source.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * App Store Connect 웹훅 수신 → 디스코드 릴레이.
 *
 * <p>애플이 밀어주는 이벤트는 빌드 업로드 상태·베타 빌드 상태·앱 버전 상태(=심사 진행)·에셋팩·
 * TestFlight 피드백 다섯 종이다. 리뷰는 여기 없어서 {@code AppStoreReviewMonitor} 가 폴링한다.
 *
 * <p>디스코드가 애플 payload 를 그대로 못 먹어서(embeds 모양이어야 한다) 이 릴레이가 필요하다.
 *
 * <p><b>공개 엔드포인트다.</b> {@code SecurityConfig} 의 {@code /api/**} permitAll 에 걸려
 * 인증 없이 열려 있으므로 HMAC 검증이 유일한 관문이다. 시크릿이 비어 있으면 요청을 받지 않는다 —
 * 검증 없이 통과시키면 URL 을 아는 누구나 운영 채널에 글을 쓸 수 있다.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class AppStoreWebhookController {

	private static final String SIGNATURE_PREFIX = "hmacsha256=";

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 애플 웹훅 등록 시 우리가 정한 시크릿. 비우면 엔드포인트가 503 으로 닫힌다. */
	@Value("${app-store.webhook.secret:}")
	private String secret;

	/**
	 * @param signature {@code x-apple-signature: hmacsha256=<hex>}. 없으면 401.
	 * @param rawBody 서명 대상은 파싱 전 원문 바이트다. DTO 로 받으면 재직렬화되어 서명이 깨진다.
	 */
	@PostMapping("/appstore")
	public ResponseEntity<Void> receive(
			@RequestHeader(value = "x-apple-signature", required = false) String signature,
			@RequestBody byte[] rawBody) {

		if (secret == null || secret.isBlank()) {
			log.error("앱스토어 웹훅 요청을 받았지만 APP_STORE_WEBHOOK_SECRET 이 비어 있다 — 검증 불가로 거절");
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		// trim: 시크릿을 파일로 봉인하면 도구가 끝에 개행을 남기기 쉽다. 그 한 바이트가
		// 키에 섞이면 서명이 영구히 불일치하고, 증상은 "전부 401" 이라 원인이 안 보인다.
		if (!isSignatureValid(secret.trim(), rawBody, signature)) {
			log.warn("앱스토어 웹훅 서명 불일치 — 거절");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		try {
			JsonNode data = objectMapper.readTree(rawBody).path("data");
			notificationService.sendAppStoreDeployNotification(
					title(data.path("type").asText("unknown")), message(data), color(data));
		} catch (Exception e) {
			// 200 을 준다. 우리 파싱이 깨진 것으로 애플의 재전송을 유발할 이유가 없다.
			log.error("앱스토어 웹훅 처리 실패", e);
		}
		return ResponseEntity.ok().build();
	}

	/**
	 * HMAC-SHA256(원문, 시크릿) 을 hex 로 만들어 헤더와 비교한다.
	 *
	 * <p>비교는 {@link MessageDigest#isEqual} 로 한다 — {@code String.equals} 는 첫 불일치에서
	 * 빠져나와 서명을 한 바이트씩 맞춰볼 여지를 준다.
	 */
	static boolean isSignatureValid(String secret, byte[] rawBody, String signatureHeader) {
		if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = toHex(mac.doFinal(rawBody));
			String actual = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();
			return MessageDigest.isEqual(
					expected.getBytes(StandardCharsets.UTF_8),
					actual.toLowerCase().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			log.error("앱스토어 웹훅 서명 계산 실패", e);
			return false;
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	static String title(String eventType) {
		return "[앱스토어 배포] " + switch (eventType) {
			case "appStoreVersionAppVersionStateUpdated" -> "앱 버전 상태 변경";
			case "buildUploadStateUpdated" -> "빌드 업로드 상태 변경";
			case "buildBetaDetailExternalBuildStateUpdated" -> "외부 베타 빌드 상태 변경";
			case "betaFeedbackCrashSubmissionCreated" -> "TestFlight 크래시 피드백";
			case "betaFeedbackScreenshotSubmissionCreated" -> "TestFlight 스크린샷 피드백";
			default -> eventType;
		};
	}

	/**
	 * attributes 를 키:값으로 그대로 펼친다.
	 *
	 * <p>ponytail: 이벤트별 상태 필드 이름을 특정하지 않는다. 종류마다 다르고 애플이 필드를
	 * 더 붙이기도 하는데, 한 키를 골라 꺼내면 그때부터 알림이 조용히 비어 온다. 전부 찍으면
	 * 코드가 짧고 새 이벤트도 그냥 나온다.
	 */
	static String message(JsonNode data) {
		JsonNode attributes = data.path("attributes");
		List<String> lines = new ArrayList<>();
		for (Iterator<Map.Entry<String, JsonNode>> it = attributes.fields(); it.hasNext(); ) {
			Map.Entry<String, JsonNode> field = it.next();
			lines.add(field.getKey() + ": " + field.getValue().asText());
		}
		String resource = data.path("relationships").path("instance").path("data").path("id").asText("");
		if (!resource.isBlank()) {
			lines.add("resourceId: " + resource);
		}
		return lines.isEmpty()
				? "```text\n(attributes 없음)\n```"
				: "```text\n" + String.join("\n", lines) + "\n```";
	}

	/** 거부·실패만 눈에 띄게 한다. 나머지는 정보성이다. */
	static String color(JsonNode data) {
		String payload = data.toString().toUpperCase();
		if (payload.contains("REJECTED") || payload.contains("FAILED") || payload.contains("CRASH")) {
			return "danger";
		}
		if (payload.contains("READY_FOR_DISTRIBUTION") || payload.contains("COMPLETE")) {
			return "good";
		}
		return "info";
	}
}
