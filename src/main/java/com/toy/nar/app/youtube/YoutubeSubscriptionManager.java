package com.toy.nar.app.youtube;

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

	@Value("${app.server.url}")
	private String baseUrl;

	/**
	 * 1. 서버 실행 직후 자동 실행
	 * (배포 후 서버가 켜지면 자동으로 구글에 구독 요청을 보냄)
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initSubscription() {
		log.info("### [System] 서버 시작: 유튜브 PubSub 전체 구독 요청 시작 ###");
		subscribeTask();
	}

	/**
	 * 2. 매일 새벽 4시에 구독 갱신 (Scheduler)
	 * (구글 구독은 유효기간이 있으므로 매일 갱신해줘야 함)
	 */
	@Scheduled(cron = "0 0 4 * * *")
	public void refreshSubscription() {
		log.info("### [Scheduler] 유튜브 PubSub 구독 정기 갱신 시작 ###");
		subscribeTask();
	}

	private void subscribeTask() {
		String callbackUrl = baseUrl + "/api/youtube/webhook";

		if (baseUrl == null || baseUrl.contains("localhost")) {
			log.warn("!!! 주의 !!! 설정된 도메인이 localhost이거나 비어있습니다. PubSub 요청이 실패할 수 있습니다. URL: {}", baseUrl);
		}

		try {
			youtubeSyncService.subscribeAllChannels(callbackUrl);
			log.info("구독 요청 전송 완료. Target Callback: {}", callbackUrl);
		} catch (Exception e) {
			log.error("구독 요청 중 에러 발생", e);
		}
	}
}