package com.toy.nar.app.data.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 서버 오류(5xx) Discord 알림. 지키려는 것은 둘이다 —
 * 같은 오류가 채널을 잠그지 않는 것(스로틀), 4xx 가 알림으로 새지 않는 것.
 */
class ServerErrorNotificationTest {

	private static final String ERROR_WEBHOOK = "https://discord.test/error-webhook";

	private RestTemplate restTemplate;
	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		restTemplate = mock(RestTemplate.class);
		notificationService = new NotificationService(restTemplate);
		ReflectionTestUtils.setField(notificationService, "notificationEnabled", true);
		ReflectionTestUtils.setField(notificationService, "discordWebhookUrl", "https://discord.test/ops-webhook");
		ReflectionTestUtils.setField(notificationService, "errorDiscordWebhookUrl", ERROR_WEBHOOK);
	}

	@Test
	@DisplayName("같은 예외가 연달아 터져도 5분 안에는 한 번만 발송한다")
	void 같은_오류는_한_번만_발송한다() {
		Throwable first = newFailure();
		Throwable second = newFailure(); // 같은 줄에서 생성 — 클래스도 최상단 스택프레임도 같다

		notificationService.sendServerErrorNotification("GET", "/api/v3/teams", first);
		notificationService.sendServerErrorNotification("GET", "/api/v3/teams", second);

		verify(restTemplate, times(1)).postForEntity(eq(ERROR_WEBHOOK), any(HttpEntity.class), eq(String.class));
	}

	@Test
	@DisplayName("다른 예외는 각각 발송한다 — 스로틀이 전부를 막아버리면 안 된다")
	void 다른_오류는_따로_발송한다() {
		notificationService.sendServerErrorNotification("GET", "/api/v3/teams", new IllegalStateException("A"));
		notificationService.sendServerErrorNotification("GET", "/api/v3/teams", new RuntimeException("B"));

		verify(restTemplate, times(2)).postForEntity(eq(ERROR_WEBHOOK), any(HttpEntity.class), eq(String.class));
	}

	@Test
	@DisplayName("전용 웹훅이 비어 있으면 운영 웹훅으로 폴백한다")
	void 전용_웹훅이_없으면_운영_웹훅으로_간다() {
		ReflectionTestUtils.setField(notificationService, "errorDiscordWebhookUrl", "");

		notificationService.sendServerErrorNotification("POST", "/api/v3/posts", new IllegalStateException("boom"));

		verify(restTemplate).postForEntity(eq("https://discord.test/ops-webhook"), any(HttpEntity.class), eq(String.class));
	}

	@Test
	@DisplayName("알림 본문에 요청 경로·예외 클래스·스택 상단이 실린다")
	void 본문에_원인이_실린다() {
		notificationService.sendServerErrorNotification("GET", "/api/v3/teams/1", new IllegalStateException("boom"));

		ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).postForEntity(eq(ERROR_WEBHOOK), captor.capture(), eq(String.class));

		Object[] embeds = (Object[]) ((Map<?, ?>) captor.getValue().getBody()).get("embeds");
		String description = String.valueOf(((Map<?, ?>) embeds[0]).get("description"));
		assertThat(description)
				.contains("GET /api/v3/teams/1")
				.contains("IllegalStateException: boom")
				.contains("at com.toy.nar.app.data.source.ServerErrorNotificationTest");
	}

	private Throwable newFailure() {
		return new IllegalStateException("boom");
	}
}
