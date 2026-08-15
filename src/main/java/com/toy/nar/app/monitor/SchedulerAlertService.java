package com.toy.nar.app.monitor;

import com.toy.nar.app.data.source.NotificationService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerAlertService {

	private static final String DEFAULT_ZONE = "Asia/Seoul";

	// 디스코드 알림·일일 요약과 별개로 Prometheus 지표를 같이 남긴다.
	// 알림은 "터졌을 때 알려주는" 용도고 지표는 "언제부터 이상했는지" 를 되짚는 용도라 역할이 다르다.
	// 잡마다 계측을 흩뿌리지 않고 모든 잡이 이미 거쳐가는 이 지점 한 곳에서만 기록한다.
	private final NotificationService notificationService;
	private final MeterRegistry meterRegistry;

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

	// Gauge 는 값 소유자를 밖에 두고 참조만 넘기는 구조라, 잡별 홀더를 여기 붙잡아 둔다.
	private final Map<String, AtomicLong> lastSuccessEpochByJob = new ConcurrentHashMap<>();
	private final Map<String, AtomicLong> zeroNewGamesStreakGaugeByJob = new ConcurrentHashMap<>();

	public void recordSuccess(String jobKey, String jobName, long durationMs) {
		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordSuccess(durationMs);

		meterRegistry.counter("nar.scheduler.runs", "job", jobKey, "outcome", "success").increment();
		meterRegistry.timer("nar.scheduler.duration", "job", jobKey).record(durationMs, TimeUnit.MILLISECONDS);
		lastSuccessEpoch(jobKey).set(Instant.now().getEpochSecond());
	}

	public void recordFailure(String jobKey, String jobName, Exception e, String detail) {
		String reason = buildReason(e);
		recordFailure(jobKey, jobName, reason, detail);
	}

	public void recordFailure(String jobKey, String jobName, String reason, String detail) {
		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordFailure(reason);

		// reason 은 라벨로 쓰지 않는다. 예외 메시지가 그대로 들어와 카디널리티가 터진다.
		meterRegistry.counter("nar.scheduler.runs", "job", jobKey, "outcome", "failure").increment();

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
			zeroNewGamesStreak(jobKey).set(0);
			return;
		}

		int streak = zeroNewGamesStreakByJob.merge(jobKey, 1, Integer::sum);
		// 임계 도달 전에도 지표는 올린다. 디스코드 알림은 3회 연속부터지만,
		// 상류 CSV 가 언제부터 밀리기 시작했는지는 1회째부터 그래프에 남아야 되짚을 수 있다.
		zeroNewGamesStreak(jobKey).set(streak);
		if (streak < zeroNewGamesThreshold) {
			return;
		}

		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordWarning("신규 게임 0건 연속");
		meterRegistry.counter("nar.scheduler.runs", "job", jobKey, "outcome", "warning").increment();

		String cooldownKey = jobKey + "|ZERO_NEW_GAMES";
		if (!shouldSend(lastWarningAlertAt, cooldownKey, warningCooldownMinutes)) {
			return;
		}

		notificationService.sendSchedulerWarningNotification(
				jobName,
				String.format("신규 게임 0건이 %d회 연속 발생했습니다. (이번 처리 행 수: %d행)", streak, totalRowsProcessed));
	}

	public void recordWarning(String jobKey, String jobName, String detail) {
		JobDailyStats stats = getStats(jobKey, jobName);
		stats.recordWarning(detail);

		meterRegistry.counter("nar.scheduler.runs", "job", jobKey, "outcome", "warning").increment();

		String cooldownKey = jobKey + "|" + sanitize(detail);
		if (!shouldSend(lastWarningAlertAt, cooldownKey, warningCooldownMinutes)) {
			return;
		}

		notificationService.sendSchedulerWarningNotification(
				jobName,
				safeText(detail, "상세 정보 없음"));
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

	/**
	 * 잡이 마지막으로 성공한 시각(epoch seconds).
	 * <p>
	 * 실행 횟수 카운터보다 이 값이 중요하다. 잡이 죽으면 카운터는 그냥 안 늘어서 눈에 띄지 않지만,
	 * 이 게이지는 {@code time() - 값} 이 계속 커져서 임계치를 자동으로 넘긴다.
	 * <p>
	 * 첫 성공 전에는 시리즈 자체가 없다. 재시작 후 한 번도 못 돈 잡은 {@code absent()} 로 잡아야 한다.
	 */
	private AtomicLong lastSuccessEpoch(String jobKey) {
		return lastSuccessEpochByJob.computeIfAbsent(jobKey, key -> {
			AtomicLong holder = new AtomicLong();
			Gauge.builder("nar.scheduler.last.success.epoch", holder, AtomicLong::get)
					.tag("job", key)
					.description("잡이 마지막으로 성공한 시각")
					.baseUnit("seconds")
					.register(meterRegistry);
			return holder;
		});
	}

	/** 신규 게임 0건이 연속으로 몇 번 나왔는지. 상류 CSV 정체를 그래프로 되짚기 위한 값이다. */
	private AtomicLong zeroNewGamesStreak(String jobKey) {
		return zeroNewGamesStreakGaugeByJob.computeIfAbsent(jobKey, key -> {
			AtomicLong holder = new AtomicLong();
			Gauge.builder("nar.scheduler.zero.new.games.streak", holder, AtomicLong::get)
					.tag("job", key)
					.description("신규 게임 0건 연속 횟수")
					.register(meterRegistry);
			return holder;
		});
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
