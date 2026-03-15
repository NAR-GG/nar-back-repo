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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

	private final RestTemplate restTemplate;
	private static final DateTimeFormatter ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Value("${notification.discord.webhook-url:}")
	private String discordWebhookUrl;

	@Value("${notification.discord.player-webhook-url:}")
	private String playerDiscordWebhookUrl;

	@Value("${notification.enabled:false}")
	private boolean notificationEnabled;

	/**
	 * 성공 알림 전송
	 */
	public void sendSuccessNotification(DataSyncResult result) {
		if (!notificationEnabled) return;

		String title = "[동기화 성공] Riot 데이터";
		String noNewDataLine = result.newGamesAdded() == 0 ? "\n참고: 신규 게임이 없어 기존 데이터 유지 상태입니다." : "";
		String message = String.format("상태: 정상 완료\n처리 시각: `%s`\n\n" +
				"```text\n" +
				"신규 게임      : %d건\n" +
				"처리 시간      : %dms\n" +
				"처리 행 수     : %d행\n" +
				"스킵 게임      : %d건\n" +
				"실패 게임      : %d건\n" +
				"```%s",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			result.newGamesAdded(),
			result.processingTimeMs(),
			result.totalRowsProcessed(),
			result.skippedGames(),
			result.failedGames(),
			noNewDataLine
		);

		sendNotification(title, message, "good");
	}

	public void sendUserCountNotification(long userCount) {
		if (!notificationEnabled) return;

		String title = "[트래픽 알림] 실시간 접속자";
		String message = String.format("상태: 임계치 도달\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"현재 접속자 : %d명\n" +
				"```",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			userCount
		);

		sendNotification(title, message, "good");
	}


	/**
	 * 실패 알림 전송
	 */
	public void sendFailureNotification(String errorMessage) {
		if (!notificationEnabled) return;

		String safeErrorMessage = (errorMessage == null || errorMessage.isBlank()) ? "원인 미상" : errorMessage;
		String message = String.format("상태: 오류 발생\n발생 시각: `%s`\n\n" +
				"```text\n%s\n```",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			safeErrorMessage
		);

		sendNotification("[동기화 실패] Riot 데이터", message, "danger");
	}

	public void sendSchedulerFailureNotification(String jobName, String detail, String errorMessage) {
		if (!notificationEnabled) return;

		String message = String.format("상태: 스케줄 실패\n발생 시각: `%s`\n\n" +
				"```text\n" +
				"작업명: %s\n" +
				"상세: %s\n" +
				"오류: %s\n" +
				"```",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			jobName,
			detail,
			errorMessage
		);

		sendNotification("[스케줄 실패 알림]", message, "danger");
	}

	public void sendSchedulerWarningNotification(String jobName, String detail) {
		if (!notificationEnabled) return;

		String message = String.format("상태: 스케줄 이상 징후\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"작업명: %s\n" +
				"상세: %s\n" +
				"```",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			jobName,
			detail
		);

		sendNotification("[스케줄 경고 알림]", message, "warning");
	}

	public void sendSchedulerSummaryNotification(String targetDate, String summary) {
		if (!notificationEnabled) return;

		String message = String.format("집계 기준일: `%s`\n발송 시각: `%s`\n\n%s",
			targetDate,
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			summary
		);

		sendNotification("[일일 스케줄 요약]", message, "info");
	}

	public void sendPlayerRankedSoloNotification(
			String playerName,
			String riotId,
			String gameName,
			String tagLine,
			String gameId,
			String championName,
			String championIconUrl) {
		if (!notificationEnabled) return;

		String opggPath = URLEncoder.encode(gameName + "-" + tagLine, StandardCharsets.UTF_8);
		String message = String.format("상태: 솔로 랭크 게임 시작 감지\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"선수명    : %s\n" +
				"계정      : %s\n" +
				"챔피언    : %s\n" +
				"큐 타입   : Ranked Solo 5v5\n" +
				"게임 ID   : %s\n" +
				"```" +
				"\n[OP.GG 바로가기](https://www.op.gg/summoners/kr/%s)",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			playerName,
			riotId,
			championName == null || championName.isBlank() ? "-" : championName,
			gameId == null ? "-" : gameId,
			opggPath
		);

		sendPlayerDiscordNotification("[선수 솔랭 시작 감지]", message, "info", championIconUrl);
	}

	/**
	 * 실제 알림 전송
	 */
	private void sendNotification(String title, String message, String color) {
		sendNotification(discordWebhookUrl, title, message, color);
	}

	private void sendNotification(String webhookUrl, String title, String message, String color) {
		try {
			if (webhookUrl != null && !webhookUrl.isEmpty()) {
				sendDiscordNotification(webhookUrl, title, message, color);
			}
		} catch (Exception e) {
			log.error("Failed to send notification", e);
		}
	}

	private void sendPlayerDiscordNotification(String title, String message, String color, String thumbnailUrl) {
		try {
			if (playerDiscordWebhookUrl != null && !playerDiscordWebhookUrl.isEmpty()) {
				sendDiscordNotification(playerDiscordWebhookUrl, title, message, color, thumbnailUrl);
			}
		} catch (Exception e) {
			log.error("Failed to send player notification", e);
		}
	}

	private void sendDiscordNotification(String webhookUrl, String title, String message, String color) {
		sendDiscordNotification(webhookUrl, title, message, color, null);
	}

	private void sendDiscordNotification(String webhookUrl, String title, String message, String color, String thumbnailUrl) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> embed = new java.util.LinkedHashMap<>();
		embed.put("title", title);
		embed.put("description", message);
		embed.put("color", mapDiscordColor(color));
		if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
			embed.put("thumbnail", Map.of("url", thumbnailUrl));
		}

		Map<String, Object> payload = Map.of(
			"username", "NAR 운영 알림",
			"embeds", new Object[] { embed }
		);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
		restTemplate.postForEntity(webhookUrl, request, String.class);
		log.info("Discord notification sent successfully");
	}

	private int mapDiscordColor(String color) {
		return switch (color) {
			case "good" -> 0x2ECC71;
			case "danger" -> 0xE74C3C;
			case "warning" -> 0xF39C12;
			case "info" -> 0x3498DB;
			default -> 0x3498DB;
		};
	}

}
