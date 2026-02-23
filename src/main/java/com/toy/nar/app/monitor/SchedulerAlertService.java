package com.toy.nar.app.monitor;

import com.toy.nar.app.data.source.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerAlertService {

	private static final String DEFAULT_ZONE = "Asia/Seoul";

	private final NotificationService notificationService;

	@Value("${notification.scheduler.summary-enabled:true}")
	private boolean summaryEnabled;

	@Value("${notification.scheduler.summary-zone:Asia/Seoul}")
	private String summaryZoneId;

	@Value("${notification.scheduler.failure-cooldown-minutes:10}")
	private long failureCooldownMinutes;

	@Value("${notification.scheduler.warning-cooldown-minutes:60}")
	private long warningCooldownMinutes;

	@Value("${notification.scheduler.zero-new-games-threshold:3}")
	private int zeroNewGamesThreshold;

	private final Map<LocalDate, Map<String, JobDailyStats>> dailyStatsByDate = new ConcurrentHashMap<>();
	private final Map<String, Instant> lastFailureAlertAt = new ConcurrentHashMap<>();
	private final Map<String, Instant> lastWarningAlertAt = new ConcurrentHashMap<>();
	private final Map<String, Integer> zeroNewGamesStreakByJob = new ConcurrentHashMap<>();

	public void recordSuccess(String jobKey, String jobName, long durationMs) {
		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordSuccess(durationMs);
	}

	public void recordFailure(String jobKey, String jobName, Exception e, String detail) {
		String reason = buildReason(e);
		recordFailure(jobKey, jobName, reason, detail);
	}

	public void recordFailure(String jobKey, String jobName, String reason, String detail) {
		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordFailure(reason);

		String cooldownKey = jobKey + "|" + sanitize(reason);
		if (!shouldSend(lastFailureAlertAt, cooldownKey, failureCooldownMinutes)) {
			return;
		}

		notificationService.sendSchedulerFailureNotification(
				jobName,
				safeText(detail, "상세 정보 없음"),
				safeText(reason, "원인 미상"));
	}

	public void trackZeroNewGames(String jobKey, String jobName, int newGamesAdded, long totalRowsProcessed) {
		if (newGamesAdded > 0) {
			zeroNewGamesStreakByJob.remove(jobKey);
			return;
		}

		int streak = zeroNewGamesStreakByJob.merge(jobKey, 1, Integer::sum);
		if (streak < zeroNewGamesThreshold) {
			return;
		}

		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordWarning("신규 게임 0건 연속");

		String cooldownKey = jobKey + "|ZERO_NEW_GAMES";
		if (!shouldSend(lastWarningAlertAt, cooldownKey, warningCooldownMinutes)) {
			return;
		}

		notificationService.sendSchedulerWarningNotification(
				jobName,
				String.format("신규 게임 0건이 %d회 연속 발생했습니다. (이번 처리 행 수: %d행)", streak, totalRowsProcessed));
	}

	@Scheduled(cron = "${notification.scheduler.daily-summary-cron:0 0 9 * * *}", zone = "${notification.scheduler.summary-zone:Asia/Seoul}")
	public void sendDailySummary() {
		if (!summaryEnabled) {
			return;
		}

		LocalDate targetDate = nowZoneDate().minusDays(1);
		Map<String, JobDailyStats> dailyStats = dailyStatsByDate.remove(targetDate);
		if (dailyStats == null || dailyStats.isEmpty()) {
			log.info("Scheduler daily summary skipped. No stats for {}", targetDate);
			return;
		}

		List<JobDailyStatsSnapshot> snapshots = dailyStats.values().stream()
				.map(JobDailyStats::snapshot)
				.sorted(Comparator.comparing(JobDailyStatsSnapshot::jobName))
				.toList();

		String summary = buildSummaryMessage(snapshots);
		notificationService.sendSchedulerSummaryNotification(targetDate.toString(), summary);
	}

	private JobDailyStats getStats(String jobKey, String jobName) {
		LocalDate today = nowZoneDate();
		Map<String, JobDailyStats> byJob = dailyStatsByDate.computeIfAbsent(today, ignored -> new ConcurrentHashMap<>());
		return byJob.computeIfAbsent(jobKey, ignored -> new JobDailyStats(jobName));
	}

	private LocalDate nowZoneDate() {
		return LocalDate.now(resolveZone());
	}

	private ZoneId resolveZone() {
		try {
			return ZoneId.of(summaryZoneId);
		} catch (Exception e) {
			log.warn("Invalid scheduler summary zone '{}'. Fallback to {}", summaryZoneId, DEFAULT_ZONE);
			return ZoneId.of(DEFAULT_ZONE);
		}
	}

	private boolean shouldSend(Map<String, Instant> tracker, String key, long cooldownMinutes) {
		Instant now = Instant.now();
		Instant lastSent = tracker.get(key);
		if (lastSent != null && now.minusSeconds(cooldownMinutes * 60).isBefore(lastSent)) {
			return false;
		}
		tracker.put(key, now);
		return true;
	}

	private String buildReason(Exception e) {
		if (e == null) {
			return "원인 미상";
		}
		String message = e.getMessage();
		String errorType = e.getClass().getSimpleName();
		if (message == null || message.isBlank()) {
			return errorType;
		}
		return errorType + ": " + message;
	}

	private String buildSummaryMessage(List<JobDailyStatsSnapshot> snapshots) {
		List<String> lines = new ArrayList<>();
		for (JobDailyStatsSnapshot snapshot : snapshots) {
			long averageDuration = snapshot.successCount() > 0
					? snapshot.totalDurationMs() / snapshot.successCount()
					: 0L;
			lines.add(String.format(
					"- %s | 성공 %d | 실패 %d | 경고 %d | 평균 %dms | 최대 %dms",
					snapshot.jobName(),
					snapshot.successCount(),
					snapshot.failureCount(),
					snapshot.warningCount(),
					averageDuration,
					snapshot.maxDurationMs()));
		}
		return String.join("\n", lines);
	}

	private String sanitize(String text) {
		if (text == null) {
			return "";
		}
		return text.length() > 120 ? text.substring(0, 120) : text;
	}

	private String safeText(String text, String fallback) {
		return (text == null || text.isBlank()) ? fallback : text;
	}

	private static final class JobDailyStats {
		private final String jobName;
		private long successCount;
		private long failureCount;
		private long warningCount;
		private long totalDurationMs;
		private long maxDurationMs;
		private String lastFailureReason;

		private JobDailyStats(String jobName) {
			this.jobName = jobName;
		}

		private synchronized void recordSuccess(long durationMs) {
			successCount++;
			totalDurationMs += Math.max(durationMs, 0L);
			maxDurationMs = Math.max(maxDurationMs, Math.max(durationMs, 0L));
		}

		private synchronized void recordFailure(String reason) {
			failureCount++;
			lastFailureReason = reason;
		}

		private synchronized void recordWarning(String reason) {
			warningCount++;
			lastFailureReason = reason;
		}

		private synchronized JobDailyStatsSnapshot snapshot() {
			return new JobDailyStatsSnapshot(
					jobName,
					successCount,
					failureCount,
					warningCount,
					totalDurationMs,
					maxDurationMs,
					lastFailureReason);
		}
	}

	private record JobDailyStatsSnapshot(
			String jobName,
			long successCount,
			long failureCount,
			long warningCount,
			long totalDurationMs,
			long maxDurationMs,
			String lastFailureReason) {
	}
}
