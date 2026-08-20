package com.toy.nar.app.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.config.CloudinaryProperties;

@ExtendWith(MockitoExtension.class)
class CloudinaryQuotaMonitorTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private NotificationService notificationService;
	@Mock
	private WebClient webClient;

	@Test
	@DisplayName("자격증명이 비면 조회조차 하지 않는다 — 로컬에서 외부 호출이 나가지 않게")
	void skipsWhenCredentialsMissing() {
		CloudinaryProperties properties = new CloudinaryProperties();
		CloudinaryQuotaMonitor monitor =
				new CloudinaryQuotaMonitor(properties, notificationService, webClient);
		ReflectionTestUtils.setField(monitor, "alertPercent", 60.0);

		monitor.checkQuota();

		verifyNoInteractions(webClient);
		verifyNoInteractions(notificationService);
	}

	@Test
	@DisplayName("used_percent 를 읽지 못하면 음수 — 0%로 읽어 조용히 넘어가면 안 된다")
	void unreadableUsageIsNegative() throws Exception {
		assertThat(CloudinaryQuotaMonitor.usedPercent(null)).isNegative();
		assertThat(CloudinaryQuotaMonitor.usedPercent(MAPPER.readTree("{}"))).isNegative();
		assertThat(CloudinaryQuotaMonitor.usedPercent(MAPPER.readTree("{\"limit\":25.0}"))).isNegative();
	}

	@Test
	@DisplayName("used_percent 를 그대로 읽는다")
	void readsUsedPercent() throws Exception {
		var credits = MAPPER.readTree("{\"usage\":3.36,\"limit\":25.0,\"used_percent\":13.44}");

		assertThat(CloudinaryQuotaMonitor.usedPercent(credits)).isEqualTo(13.44);
	}
}
