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
import java.util.List;
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

	@Value("${notification.discord.roster-webhook-url:}")
	private String rosterDiscordWebhookUrl;

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

	public void sendPlayerGameNotification(
			String playerName,
			String riotId,
			String gameName,
			String tagLine,
			String gameId,
			String queueDisplayName,
			String championName,
			String championIconUrl) {
		if (!notificationEnabled) return;

		String opggLink = buildOpggLink(gameName, tagLine);
		String normalizedQueueDisplayName = queueDisplayName == null || queueDisplayName.isBlank() ? "기타 게임" : queueDisplayName;
		String message = String.format("상태: %s 시작 감지\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"선수명    : %s\n" +
				"계정      : %s\n" +
				"챔피언    : %s\n" +
				"큐 타입   : %s\n" +
				"게임 ID   : %s\n" +
				"```",
			normalizedQueueDisplayName,
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			playerName,
			riotId,
			championName == null || championName.isBlank() ? "-" : championName,
			normalizedQueueDisplayName,
			gameId == null ? "-" : gameId
		);
		if (!opggLink.isBlank()) {
			message += "\n" + opggLink;
		}

		sendPlayerDiscordNotification("[선수 게임 시작 감지]", message, "info", championIconUrl);
	}

	/**
	 * LCK 등 라이브 경기 시작 감지 알림 (운영 웹훅으로 발송)
	 */
	public void sendLiveMatchNotification(
			String leagueName,
			String blueTeamName,
			String redTeamName,
			String gameId,
			String matchId) {
		if (!notificationEnabled) return;

		String league = leagueName == null || leagueName.isBlank() ? "LoL Esports" : leagueName;
		String blue = blueTeamName == null || blueTeamName.isBlank() ? "?" : blueTeamName;
		String red = redTeamName == null || redTeamName.isBlank() ? "?" : redTeamName;
		String message = String.format("상태: 라이브 경기 시작 감지\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"리그    : %s\n" +
				"대진    : %s vs %s\n" +
				"게임 ID : %s\n" +
				"매치 ID : %s\n" +
				"```",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			league,
			blue,
			red,
			gameId == null || gameId.isBlank() ? "-" : gameId,
			matchId == null || matchId.isBlank() ? "-" : matchId
		);

		sendNotification(String.format("[%s 라이브 경기 시작]", league), message, "danger");
	}

	/**
	 * 라이브 경기 중 오브젝트(타워/바론/드래곤/억제기) 이벤트 알림 (운영 웹훅으로 발송)
	 */
	public void sendLiveObjectEventNotification(
			String leagueName,
			String teamName,
			String teamSide,
			String eventType,
			String eventSubType,
			int eventOrder,
			String gameId) {
		if (!notificationEnabled) return;

		String league = leagueName == null || leagueName.isBlank() ? "LoL Esports" : leagueName;
		String team = teamDisplay(teamSide, teamName);
		String label = liveEventLabel(eventType, eventSubType);
		String color = eventType == null ? "info"
				: switch (eventType.toUpperCase()) {
					case "BARON" -> "warning";
					default -> "good";
				};

		String message = String.format("리그: %s\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"팀      : %s\n" +
				"이벤트  : %s (%d번째)\n" +
				"게임 ID : %s\n" +
				"```",
			league,
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			team,
			label,
			eventOrder,
			gameId == null || gameId.isBlank() ? "-" : gameId
		);

		sendNotification(String.format("[%s] %s — %s", league, label, team), message, color);
	}

	/**
	 * 라이브 경기 중 킬 이벤트 알림 — 킬러/피해자(선수·챔피언) 포함 (운영 웹훅으로 발송)
	 */
	public void sendLiveKillNotification(
			String leagueName,
			String killerTeamName,
			String killerTeamSide,
			String killerName,
			String killerChampion,
			String victimName,
			String victimChampion,
			int killOrder,
			String gameId) {
		if (!notificationEnabled) return;

		String league = leagueName == null || leagueName.isBlank() ? "LoL Esports" : leagueName;
		String killer = playerWithChampion(killerName, killerChampion);
		String victim = playerWithChampion(victimName, victimChampion);
		String killerTeam = teamDisplay(killerTeamSide, killerTeamName);

		String message = String.format("리그: %s\n감지 시각: `%s`\n\n" +
				"```text\n" +
				"킬러   : %s  [%s]\n" +
				"피해자 : %s\n" +
				"팀 킬   : %d번째\n" +
				"게임 ID : %s\n" +
				"```",
			league,
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			killer,
			killerTeam,
			victim,
			killOrder,
			gameId == null || gameId.isBlank() ? "-" : gameId
		);

		sendNotification(String.format("[%s] ⚔️ %s → %s", league, killer, victim), message, "danger");
	}

	/**
	 * LCK 1군 로스터 변동(이적 의심) 알림. 자동 반영이 아니라 백오피스에서 확정하라는 안내다.
	 * 전용 웹훅이 없으면 운영 웹훅으로 보낸다.
	 */
	public void sendLckRosterDiffNotification(List<String> diffLines) {
		if (!notificationEnabled || diffLines == null || diffLines.isEmpty()) return;

		String message = String.format("상태: 소속팀 불일치 감지\n감지 시각: `%s`\n\n" +
				"```text\n%s\n```\n" +
				"백오피스 → 선수 관리에서 소속팀을 확정해 주세요. 자동 반영되지 않습니다.",
			LocalDateTime.now().format(ALERT_TIME_FORMATTER),
			String.join("\n", diffLines)
		);

		String webhookUrl = (rosterDiscordWebhookUrl == null || rosterDiscordWebhookUrl.isEmpty())
				? discordWebhookUrl
				: rosterDiscordWebhookUrl;
		sendNotification(webhookUrl, "[LCK 로스터 변동 감지]", message, "warning");
	}

	private String teamDisplay(String teamSide, String teamName) {
		String emoji = "Blue".equalsIgnoreCase(teamSide) ? "🔵"
				: "Red".equalsIgnoreCase(teamSide) ? "🔴" : "";
		String name = teamName == null || teamName.isBlank()
				? ("Blue".equalsIgnoreCase(teamSide) ? "블루" : "Red".equalsIgnoreCase(teamSide) ? "레드" : "-")
				: teamName;
		return (emoji.isBlank() ? "" : emoji + " ") + name;
	}

	private String playerWithChampion(String playerName, String champion) {
		String name = playerName == null || playerName.isBlank() ? "?" : playerName;
		if (champion == null || champion.isBlank()) {
			return name;
		}
		return name + " (" + champion + ")";
	}

	private String liveEventLabel(String eventType, String eventSubType) {
		if (eventType == null) {
			return "이벤트";
		}
		return switch (eventType.toUpperCase()) {
			case "TOWER" -> "🗼 포탑 파괴";
			case "BARON" -> "🟣 바론 처치";
			case "INHIBITOR" -> "🛡️ 억제기 파괴";
			case "DRAGON" -> "🐉 드래곤 처치"
					+ (eventSubType == null || eventSubType.isBlank() ? "" : " (" + eventSubType + ")");
			case "KILL" -> "⚔️ 킬";
			default -> eventType;
		};
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

	private String buildOpggLink(String gameName, String tagLine) {
		if (gameName == null || gameName.isBlank() || tagLine == null || tagLine.isBlank()) {
			return "";
		}
		String opggPath = URLEncoder.encode(gameName + "-" + tagLine, StandardCharsets.UTF_8);
		return String.format("[OP.GG 바로가기](https://www.op.gg/summoners/kr/%s)", opggPath);
	}

}
