package com.toy.nar.app.mobile.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 소리 나는 발송 payload 를 지키는 회귀 테스트.
 *
 * <p>알림 잠자기는 무음 발송이 아니라 발송 자체를 건너뛰는 방식이라
 * ({@link QuietAwarePushSender}) 게이트웨이가 만드는 payload 는 한 종류뿐이다.
 * 여기가 조용히 바뀌면 <b>모든</b> 알림의 소리·배너가 사라지는데 예외도 로그도 안 남는다.</p>
 *
 * <p>firebase-admin(9.7.0 고정)의 AndroidConfig/ApnsConfig/Aps 는 Google HTTP client
 * {@code @Key} 직렬화 기반이라 public 게터가 없다. 소스 jar 로 확인한 실제 구조:</p>
 * <ul>
 *   <li>AndroidConfig 의 "priority" 필드는 {@code Priority} enum 이 아니라 그 이름을 소문자로
 *       바꾼 {@code String}("high"/"normal")이다 — 빌더가 생성 시점에 변환한다.</li>
 *   <li>ApnsConfig 는 "aps" 필드를 따로 갖지 않는다. 생성 시점에 Aps.getFields() 를
 *       "payload"라는 Map 필드에 key "aps" 로 즉시 합쳐 넣는다. Aps 자신도 sound 등
 *       개별 필드가 없고 전부 "fields" Map 에 담아 둔다.</li>
 * </ul>
 */
class FirebaseMobilePushGatewayTest {

	@SuppressWarnings("unchecked")
	private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider = mock(ObjectProvider.class);

	private final FirebaseMobilePushGateway gateway =
			new FirebaseMobilePushGateway(firebaseMessagingProvider);

	@Test
	@DisplayName("안드로이드는 HIGH 우선순위로 보내고 채널을 지정하지 않는다")
	void 안드로이드는_HIGH_우선순위로_보낸다() {
		AndroidConfig config = gateway.androidConfig();

		// HIGH 가 죽으면 기존 알림 전부의 헤드업(배너) 표시가 조용히 사라진다.
		assertThat(ReflectionTestUtils.getField(config, "priority")).isEqualTo("high");
		// 채널을 지정하지 않으므로 앱 매니페스트 기본 채널로 간다.
		assertThat(ReflectionTestUtils.getField(config, "notification")).isNull();
	}

	@Test
	@DisplayName("iOS 는 기본 사운드로 보낸다")
	@SuppressWarnings("unchecked")
	void iOS_는_기본_사운드로_보낸다() {
		ApnsConfig config = gateway.apnsConfig();

		Map<String, Object> payload = (Map<String, Object>) ReflectionTestUtils.getField(config, "payload");
		Map<String, Object> apsFields = (Map<String, Object>) payload.get("aps");
		assertThat(apsFields.get("sound")).isEqualTo("default");
		// 잠자기를 무음 발송으로 되돌리면 이 단정이 먼저 깨진다.
		assertThat(apsFields.get("interruption-level")).isNull();
	}
}
