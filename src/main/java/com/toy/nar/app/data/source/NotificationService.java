package com.toy.nar.app.data.source;

import com.toy.nar.app.data.source.dto.DataSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

	private final RestTemplate restTemplate;

	@Value("${notification.slack.webhook-url:}")
	private String slackWebhookUrl;

	@Value("${notification.enabled:false}")
	private boolean notificationEnabled;

	/**
	 * 성공 알림 전송
	 */
	public void sendSuccessNotification(DataSyncResult result) {
		if (!notificationEnabled) return;

		String message = String.format("Riot 데이터 추가 완료\n" +
				"New games: %d\n" +
				"Processing time: %dms\n" +
				"Time: %s",
			result.newGamesAdded(),
			result.processingTimeMs(),
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
		);

		sendNotification("Data Sync Success", message, "good");
	}

	public void sendUserCountNotification(long userCount) {
		if (!notificationEnabled) return;

		String title = "[축하] 실시간 접속자 수 돌파!";
		String message = String.format("현재 실시간 접속자 수가 %d명을 돌파했습니다.", userCount);

		sendNotification(title, message, "good");
	}


	/**
	 * 실패 알림 전송
	 */
	public void sendFailureNotification(String errorMessage) {
		if (!notificationEnabled) return;

		String message = String.format("**Data Sync Failed**\n" +
				"Error: %s\n" +
				"Time: %s",
			errorMessage,
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
		);

		sendNotification("Data Sync Failed", message, "danger");
	}

	/**
	 * 실제 알림 전송
	 */
	private void sendNotification(String title, String message, String color) {
		try {
			// Slack 알림
			if (slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
				sendSlackNotification(title, message, color);
			}

		} catch (Exception e) {
			log.error("Failed to send notification", e);
		}
	}

	private void sendSlackNotification(String title, String message, String color) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> payload = Map.of(
			"attachments", new Object[]{
				Map.of(
					"title", title,
					"text", message,
					"color", color
				)
			}
		);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
		restTemplate.postForEntity(slackWebhookUrl, request, String.class);
		log.info("Slack notification sent successfully");
	}

}