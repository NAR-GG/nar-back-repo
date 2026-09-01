package com.toy.nar.api.webhook;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱스토어 웹훅은 {@code /api/**} permitAll 에 걸려 인증 없이 열려 있다.
 * HMAC 검증이 유일한 관문이라, 통과 조건이 정확히 하나뿐인지 잠근다.
 */
class AppStoreWebhookControllerTest {

	private static final String SECRET = "s3cr3t";
	private static final byte[] BODY = "{\"data\":{\"type\":\"buildUploadStateUpdated\"}}"
			.getBytes(StandardCharsets.UTF_8);
	/** 위 SECRET·BODY 의 HMAC-SHA256 hex. 값 자체가 아니라 "우리 계산과 일치"를 본다. */
	private static final String VALID_HEX = hmacHex();

	@Test
	@DisplayName("올바른 서명은 통과한다")
	void validSignature() {
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, BODY, "hmacsha256=" + VALID_HEX))
				.isTrue();
	}

	@Test
	@DisplayName("본문이 한 바이트라도 바뀌면 거절한다")
	void tamperedBody() {
		byte[] tampered = "{\"data\":{\"type\":\"buildUploadStateUpdatee\"}}".getBytes(StandardCharsets.UTF_8);
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, tampered, "hmacsha256=" + VALID_HEX))
				.isFalse();
	}

	@Test
	@DisplayName("시크릿이 다르면 거절한다")
	void wrongSecret() {
		assertThat(AppStoreWebhookController.isSignatureValid("other", BODY, "hmacsha256=" + VALID_HEX))
				.isFalse();
	}

	@Test
	@DisplayName("헤더가 없거나 접두사가 없으면 거절한다 — 서명 없는 요청이 조용히 통과하지 않는다")
	void missingOrMalformedHeader() {
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, BODY, null)).isFalse();
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, BODY, "")).isFalse();
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, BODY, VALID_HEX)).isFalse();
		assertThat(AppStoreWebhookController.isSignatureValid(SECRET, BODY, "sha256=" + VALID_HEX)).isFalse();
	}

	@Test
	@DisplayName("attributes 를 키:값으로 그대로 펼친다 — 이벤트별 상태 필드 이름을 특정하지 않는다")
	void messageDumpsAllAttributes() throws Exception {
		String payload = """
				{"data":{"type":"appStoreVersionAppVersionStateUpdated",
				 "attributes":{"timestamp":"2026-09-01T00:00:00Z","newValue":"IN_REVIEW"},
				 "relationships":{"instance":{"data":{"id":"abc-123"}}}}}
				""";
		var data = new ObjectMapper().readTree(payload).path("data");

		assertThat(AppStoreWebhookController.message(data))
				.contains("newValue: IN_REVIEW")
				.contains("timestamp: 2026-09-01T00:00:00Z")
				.contains("resourceId: abc-123");
		assertThat(AppStoreWebhookController.title(data.path("type").asText()))
				.isEqualTo("[앱스토어 배포] 앱 버전 상태 변경");
		assertThat(AppStoreWebhookController.color(data)).isEqualTo("info");
	}

	@Test
	@DisplayName("거부·실패는 빨강, 승인·완료는 초록")
	void colorByState() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		assertThat(AppStoreWebhookController.color(
				mapper.readTree("{\"attributes\":{\"newValue\":\"REJECTED\"}}"))).isEqualTo("danger");
		assertThat(AppStoreWebhookController.color(
				mapper.readTree("{\"attributes\":{\"newValue\":\"READY_FOR_DISTRIBUTION\"}}"))).isEqualTo("good");
	}

	private static String hmacHex() {
		try {
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			StringBuilder sb = new StringBuilder();
			for (byte b : mac.doFinal(BODY)) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
