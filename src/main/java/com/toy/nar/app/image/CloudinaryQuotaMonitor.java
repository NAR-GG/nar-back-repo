package com.toy.nar.app.image;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.config.CloudinaryProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cloudinary 무료 티어 credits 소진을 감시한다.
 *
 * <p>앱 이미지가 전부 Cloudinary 를 타므로(회원 프로필·선수·팀·챔피언) 쿼터가 넘으면 딜리버리가
 * 막혀 전 구간이 깨진다. 킬 스위치({@code CLOUDINARY_CDN_ENABLED=false} + 언랩 SQL)는 있지만
 * 터진 걸 알아야 쓸 수 있어서, 넘기 전에 알린다.
 *
 * <p>Cloudinary 에는 사용량 임계 알림이 없다 — 웹훅 타입이 전부 자산 이벤트(upload·delete·eager 등)라
 * AWS Budgets 같은 것이 없다. 그래서 Admin API 를 우리가 당겨온다.
 *
 * <p>집계가 1~2일 늦는다({@code last_updated} 가 조회일보다 과거다). 임계를 낮게 잡는 이유이고,
 * 알림이 왔을 때 실제 사용량은 더 높을 수 있다는 뜻이기도 하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryQuotaMonitor {

	private static final String USAGE_URL = "https://api.cloudinary.com/v1_1/{cloud}/usage";

	private final CloudinaryProperties properties;
	private final NotificationService notificationService;
	private final WebClient webClient;

	/** 집계 지연을 감안해 낮게 잡는다. 80%에 걸면 알림이 왔을 때 이미 넘겼을 수 있다. */
	@Value("${cloudinary.quota-alert-percent:60}")
	private double alertPercent;

	@Scheduled(cron = "${cloudinary.quota-check-cron:0 0 10 * * *}", zone = "Asia/Seoul")
	public void checkQuota() {
		if (isBlank(properties.getCloudName()) || isBlank(properties.getApiKey())
				|| isBlank(properties.getApiSecret())) {
			return;
		}

		JsonNode usage;
		try {
			usage = webClient.get()
					.uri(USAGE_URL, properties.getCloudName())
					.header(HttpHeaders.AUTHORIZATION, basicAuth())
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (Exception e) {
			log.warn("Cloudinary 사용량 조회 실패: {}", e.getMessage());
			return;
		}
		if (usage == null || usage.path("credits").isMissingNode()) {
			log.warn("Cloudinary 사용량 응답에 credits 가 없다");
			return;
		}

		JsonNode credits = usage.path("credits");
		double used = usedPercent(credits);
		if (used < 0) {
			log.warn("Cloudinary used_percent 를 읽지 못했다");
			return;
		}

		String detail = String.format(
				"credits %.2f / %.1f (%.1f%%), 임계 %.0f%% · 집계 기준일 %s"
						+ " — 넘으면 앱 이미지 전 구간이 깨진다. 되돌리려면 CLOUDINARY_CDN_ENABLED=false + 언랩 SQL.",
				credits.path("usage").asDouble(),
				credits.path("limit").asDouble(),
				used,
				alertPercent,
				usage.path("last_updated").asText("알 수 없음"));

		if (used >= alertPercent) {
			notificationService.sendSchedulerWarningNotification("Cloudinary 쿼터", detail);
		}
		log.info("Cloudinary 쿼터 점검 — {}", detail);
	}

	/** 읽지 못하면 음수. 응답 모양이 바뀌었을 때 0%로 읽어 조용히 넘어가는 것을 막는다. */
	static double usedPercent(JsonNode credits) {
		return credits == null ? -1 : credits.path("used_percent").asDouble(-1);
	}

	private String basicAuth() {
		String raw = properties.getApiKey() + ":" + properties.getApiSecret();
		return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
