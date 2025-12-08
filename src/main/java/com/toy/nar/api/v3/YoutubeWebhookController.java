package com.toy.nar.api.v3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.toy.nar.app.youtube.YoutubeSyncService;
import com.toy.nar.app.youtube.dto.PubSubFeed;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Hidden
@Slf4j
@RestController
@RequestMapping("/api/youtube")
@RequiredArgsConstructor
public class YoutubeWebhookController {

	private final YoutubeSyncService youtubeSyncService;

	@Value("${app.server.url}")
	private String configBaseUrl;

	/**
	 * 구독 확인 (Challenge)
	 * 구글이 GET 요청으로 hub.challenge를 보내면 그대로 반환해야 구독이 성립됨
	 */
	@GetMapping("/webhook")
	public ResponseEntity<String> verifySubscription(
		@RequestParam("hub.mode") String mode,
		@RequestParam("hub.topic") String topic,
		@RequestParam("hub.challenge") String challenge,
		@RequestParam(value = "hub.lease_seconds", required = false) String leaseSeconds) {

		log.info("YouTube PubSub 구독 확인 요청: mode={}, topic={}", mode, topic);
		return ResponseEntity.ok(challenge);
	}

	/**
	 * 알림 수신 (Notification)
	 * 새로운 영상이 올라오면 XML 데이터가 POST로 옴
	 */
	@PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_ATOM_XML_VALUE)
	public ResponseEntity<Void> receiveNotification(@RequestBody PubSubFeed feed) {
		if (feed.getEntries() != null && !feed.getEntries().isEmpty()) {
			feed.getEntries().stream()
				.filter(entry -> entry.isVideoNotification()) // 비디오 알림만 필터링
				.forEach(entry -> {
					log.info("New Video Detected: {} (VideoId: {})", entry.getTitle(), entry.getVideoId());
					youtubeSyncService.processNewVideoNotification(entry.getVideoId(), entry.getChannelId());
				});
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/subscribe-all")
	public String triggerSubscribe(@RequestParam String baseUrl) {
		String targetUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : configBaseUrl;
		youtubeSyncService.subscribeAllChannels(baseUrl);
		return "구독 요청을 전송했습니다. 로그를 확인하세요. Target: " + baseUrl;
	}
}
