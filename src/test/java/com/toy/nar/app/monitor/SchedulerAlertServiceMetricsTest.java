package com.toy.nar.app.monitor;

import com.toy.nar.app.data.source.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 지표가 Prometheus 로 나가는지 검증한다.
 * <p>
 * 디스코드 알림 쪽 동작(쿨다운·요약)은 기존대로 두고, 여기서는 지표만 본다.
 */
class SchedulerAlertServiceMetricsTest {

	private static final String JOB_KEY = "MATCH_SYNC";
	private static final String JOB_NAME = "리그 경기 동기화";

	private MeterRegistry registry;
	private SchedulerAlertService service;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		service = new SchedulerAlertService(Mockito.mock(NotificationService.class), registry);
		// @Value 는 순수 단위 테스트에서 주입되지 않아 기본값이 0 이 된다.
		// 0 이면 신규 게임 0건 경고가 첫 회부터 발사되므로 운영 기본값(3)을 넣어준다.
		ReflectionTestUtils.setField(service, "zeroNewGamesThreshold", 3);
	}

	@Test
	@DisplayName("성공하면 카운터·타이머·마지막 성공 시각이 모두 남는다")
	void recordSuccess_publishesMetrics() {
		long before = Instant.now().getEpochSecond();

		service.recordSuccess(JOB_KEY, JOB_NAME, 1_500L);

		assertThat(runs("success")).isEqualTo(1.0);
		assertThat(registry.timer("nar.scheduler.duration", "scheduler_job", JOB_KEY).count()).isEqualTo(1L);
		assertThat(lastSuccessEpoch()).isGreaterThanOrEqualTo(before);
	}

	@Test
	@DisplayName("실패는 outcome=failure 로만 집계되고 마지막 성공 시각은 건드리지 않는다")
	void recordFailure_doesNotTouchLastSuccess() {
		service.recordSuccess(JOB_KEY, JOB_NAME, 100L);
		double successEpoch = lastSuccessEpoch();

		service.recordFailure(JOB_KEY, JOB_NAME, "CONNECT_TIMEOUT", "상세");

		assertThat(runs("failure")).isEqualTo(1.0);
		assertThat(runs("success")).isEqualTo(1.0);
		assertThat(lastSuccessEpoch()).isEqualTo(successEpoch);
	}

	@Test
	@DisplayName("신규 게임 0건 연속 횟수는 임계 도달 전에도 올라가고, 게임이 들어오면 0으로 돌아간다")
	void zeroNewGamesStreak_tracksBeforeThresholdAndResets() {
		service.trackZeroNewGames(JOB_KEY, JOB_NAME, 0, 120L);
		assertThat(zeroNewGamesStreak()).isEqualTo(1.0);

		service.trackZeroNewGames(JOB_KEY, JOB_NAME, 0, 120L);
		assertThat(zeroNewGamesStreak()).isEqualTo(2.0);

		service.trackZeroNewGames(JOB_KEY, JOB_NAME, 5, 120L);
		assertThat(zeroNewGamesStreak()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("실패 사유는 라벨에 들어가지 않는다 — 예외 메시지가 라벨이 되면 카디널리티가 터진다")
	void failureReason_isNotUsedAsLabel() {
		service.recordFailure(JOB_KEY, JOB_NAME, "reason-A", "상세");
		service.recordFailure(JOB_KEY, JOB_NAME, "reason-B", "상세");

		long seriesCount = registry.find("nar.scheduler.runs").counters().size();
		assertThat(seriesCount).isEqualTo(1L);
		assertThat(runs("failure")).isEqualTo(2.0);
	}

	private double runs(String outcome) {
		return registry.counter("nar.scheduler.runs", "scheduler_job", JOB_KEY, "outcome", outcome).count();
	}

	private double lastSuccessEpoch() {
		return registry.get("nar.scheduler.last.success.epoch").tag("scheduler_job", JOB_KEY).gauge().value();
	}

	private double zeroNewGamesStreak() {
		return registry.get("nar.scheduler.zero.new.games.streak").tag("scheduler_job", JOB_KEY).gauge().value();
	}
}
