package com.toy.nar.app.youtube;

import com.toy.nar.app.monitor.SchedulerAlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSubscriptionManager {

	private final YoutubeSyncService youtubeSyncService;
	private final SchedulerAlertService schedulerAlertService;

	@Value("${app.server.url}")
	private String baseUrl;

	/**
	 * 1. 서버 실행 직후 자동 실행
	 * (배포 후 서버가 켜지면 자동으로 구글에 구독 요청을 보냄)
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initSubscription() {
		log.info("### [System] 서버 시작: 유튜브 PubSub 전체 구독 요청 시작 ###");
		try {
			subscribeTask();
		} catch (Exception e) {
			log.error("초기 구독 요청 중 에러 발생", e);
		}
	}

	/**
	 * 2. 매일 새벽 4시에 구독 갱신 (Scheduler)
	 * (구글 구독은 유효기간이 있으므로 매일 갱신해줘야 함)
	 */
	@Scheduled(cron = "0 0 4 * * *")
	public void refreshSubscription() {
		log.info("### [Scheduler] 유튜브 PubSub 구독 정기 갱신 시작 ###");
		long startTime = System.currentTimeMillis();
		try {
			subscribeTask();
			long elapsed = System.currentTimeMillis() - startTime;
			schedulerAlertService.recordSuccess("YOUTUBE_SUBSCRIPTION_REFRESH", "유튜브 PubSub 구독 갱신", elapsed);
		} catch (Exception e) {
			log.error("구독 요청 중 에러 발생", e);
			schedulerAlertService.recordFailure(
					"YOUTUBE_SUBSCRIPTION_REFRESH",
					"유튜브 PubSub 구독 갱신",
					e,
					"baseUrl=" + baseUrl);
		}
	}

	private void subscribeTask() {
		// [수정] 여기서 경로를 붙이지 말고 baseUrl만 그대로 넘깁니다.
		// YoutubeSyncService 내부에서 "/api/youtube/webhook"을 붙이기 때문입니다.
		String targetDomain = baseUrl;

		if (targetDomain == null || targetDomain.contains("localhost")) {
			log.warn("!!! 주의 !!! 설정된 도메인이 localhost이거나 비어있습니다. PubSub 요청이 실패할 수 있습니다. URL: {}", targetDomain);
		}

		// Service에게 도메인만 전달 (예: https://api.nar.kr)
		youtubeSyncService.subscribeAllChannels(targetDomain);

		// 로그에는 헷갈리지 않게 실제 호출될 예상 URL을 찍어줍니다.
		log.info("구독 요청 전송 완료. Target Base URL: {}", targetDomain);
	}
}
