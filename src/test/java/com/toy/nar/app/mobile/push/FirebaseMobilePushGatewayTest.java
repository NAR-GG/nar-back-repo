package com.toy.nar.app.mobile.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
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
 * 무음/소리 분기가 실제 FCM payload 필드에 반영되는지 지키는 회귀 테스트.
 *
 * Android O+ 는 채널 설정이 payload 의 priority 보다 우선한다. priority 만 낮추고
 * 채널 지정(setChannelId)을 빼먹으면 앱은 여전히 소리·배너를 낸다 — 이 회귀는
 * 겉보기 API 호출만으로는 안 걸러지고, 실제로 만들어진 AndroidConfig 안을
 * 들여다봐야 걸린다.
 *
 * firebase-admin(9.7.0 고정)의 AndroidConfig/ApnsConfig/Aps 는 Google HTTP client
 * {@code @Key} 직렬화 기반이라 public 게터가 없다. 소스 jar로 확인한 실제 구조:
 * - AndroidConfig 는 "notification" 필드(AndroidNotification, silent=false 면 null)를 갖고,
 *   AndroidNotification 은 "channelId" 필드를 갖는다.
 * - ApnsConfig 는 "aps" 필드를 따로 갖지 않는다. 생성 시점에 Aps.getFields()를
 *   "payload"라는 Map<String,Object> 필드에 key "aps"로 즉시 합쳐 넣는다.
 *   Aps 자신도 sound 등 개별 필드가 없고, 전부 "fields"라는 Map<String,Object>에
 *   담아 둔다. 그래서 sound/interruption-level 은 payload.get("aps") 로 얻은
 *   Map 에서 꺼내야 한다.
 */
class FirebaseMobilePushGatewayTest {

	@SuppressWarnings("unchecked")
	private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider = mock(ObjectProvider.class);

	private final FirebaseMobilePushGateway gateway =
			new FirebaseMobilePushGateway(firebaseMessagingProvider);

	@Test
	@DisplayName("무음이면_안드로이드_채널을_warding_quiet_로_지정한다")
	void 무음이면_안드로이드_채널을_warding_quiet_로_지정한다() {
		MobilePushMessage message = new MobilePushMessage("제목", "본문", Map.of()).asSilent();

		AndroidConfig config = gateway.androidConfig(message);

		AndroidNotification notification =
				(AndroidNotification) ReflectionTestUtils.getField(config, "notification");
		assertThat(notification).isNotNull();
		assertThat(ReflectionTestUtils.getField(notification, "channelId")).isEqualTo("warding_quiet");
	}

	@Test
	@DisplayName("소리가_나면_안드로이드_알림_설정을_붙이지_않는다")
	void 소리가_나면_안드로이드_알림_설정을_붙이지_않는다() {
		MobilePushMessage message = new MobilePushMessage("제목", "본문", Map.of());

		AndroidConfig config = gateway.androidConfig(message);

		assertThat(ReflectionTestUtils.getField(config, "notification")).isNull();
	}

	@Test
	@DisplayName("무음이면_소리를_비우고_passive_로_보낸다")
	@SuppressWarnings("unchecked")
	void 무음이면_소리를_비우고_passive_로_보낸다() {
		MobilePushMessage message = new MobilePushMessage("제목", "본문", Map.of()).asSilent();

		ApnsConfig config = gateway.apnsConfig(message);

		Map<String, Object> payload = (Map<String, Object>) ReflectionTestUtils.getField(config, "payload");
		Map<String, Object> apsFields = (Map<String, Object>) payload.get("aps");
		assertThat(apsFields.get("sound")).isNull();
		assertThat(apsFields.get("interruption-level")).isEqualTo("passive");
	}

	@Test
	@DisplayName("소리가_나면_기본_사운드를_보낸다")
	@SuppressWarnings("unchecked")
	void 소리가_나면_기본_사운드를_보낸다() {
		MobilePushMessage message = new MobilePushMessage("제목", "본문", Map.of());

		ApnsConfig config = gateway.apnsConfig(message);

		Map<String, Object> payload = (Map<String, Object>) ReflectionTestUtils.getField(config, "payload");
		Map<String, Object> apsFields = (Map<String, Object>) payload.get("aps");
		assertThat(apsFields.get("sound")).isEqualTo("default");
	}
}
