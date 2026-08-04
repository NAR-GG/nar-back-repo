package com.toy.nar.app.mobile.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실제 APNs 에 붙어 인증만 확인하는 로컬 전용 스모크 테스트.
 *
 * <p>다른 테스트는 발송을 전부 mock 으로 덮어서 JWT 서명·헤더·토픽이 맞는지는 검증되지 않는다.
 * 여기서는 더미 디바이스 토큰으로 한 번 쏴 APNs 의 거절 사유를 본다:</p>
 * <ul>
 *   <li>{@code 400 BadDeviceToken} — 인증 통과. 토큰만 가짜라서 거절된 것이므로 키·팀·kid·토픽이 옳다.</li>
 *   <li>{@code 403 InvalidProviderToken} — JWT 가 틀렸다. 키 파일·keyId·teamId 를 확인해야 한다.</li>
 *   <li>{@code 403 MissingTopic} / {@code 400 TopicDisallowed} — apns-topic 이 틀렸다.</li>
 * </ul>
 *
 * <p>키 파일 경로가 필요하고 외부 네트워크를 타므로 기본 빌드에서는 건너뛴다. 실행:</p>
 * <pre>
 * ./gradlew test --tests "*ApnsLiveActivityClientLocalSmokeTest" -i \
 *   -Dapns.local.enabled=true \
 *   -Dapns.key-path=/path/AuthKey_XXXXXXXXXX.p8 \
 *   -Dapns.key-id=XXXXXXXXXX -Dapns.team-id=YYYYYYYYYY -Dapns.bundle-id=com.nar.wardingapp
 * </pre>
 * 결과 판정은 콘솔의 {@code APNs Live Activity 발송 실패 status=... body=...} 로그로 한다.
 */
@EnabledIfSystemProperty(named = "apns.local.enabled", matches = "true")
class ApnsLiveActivityClientLocalSmokeTest {

	@Test
	void 더미_토큰으로_APNs_인증을_확인한다() {
		ApnsLiveActivityClient client = new ApnsLiveActivityClient();
		ReflectionTestUtils.setField(client, "enabled", true);
		ReflectionTestUtils.setField(client, "keyPath", required("apns.key-path"));
		ReflectionTestUtils.setField(client, "keyId", required("apns.key-id"));
		ReflectionTestUtils.setField(client, "teamId", required("apns.team-id"));
		ReflectionTestUtils.setField(client, "bundleId", required("apns.bundle-id"));
		ReflectionTestUtils.setField(client, "host",
				System.getProperty("apns.host", "https://api.push.apple.com"));

		assertThat(client.isAvailable()).isTrue();

		// 실제 액티비티 토큰과 같은 길이(64 hex)의 존재하지 않는 토큰.
		String dummyToken = "0".repeat(64);
		Map<String, Object> contentState = new LinkedHashMap<>();
		contentState.put("phase", "playing");
		contentState.put("setNumber", 1);
		contentState.put("scoreA", 0);
		contentState.put("scoreB", 0);
		contentState.put("statusLabel", "");

		// 토큰이 가짜라 성공할 수 없다. 예외 없이 돌아오면 요청 자체는 나간 것이고,
		// 인증 성공/실패는 로그의 status·reason 으로 가린다.
		client.sendUpdate(dummyToken, contentState);
	}

	private String required(String key) {
		String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("-D" + key + " 를 지정해야 합니다.");
		}
		return value;
	}
}
