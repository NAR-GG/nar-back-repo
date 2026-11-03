package com.toy.nar.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.toy.nar.domain.sync.repository.SchedulerLeaseRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 스케줄러 전역 스위치가 실제로 스케줄링 등록을 좌우하는지 검증.
 * app.scheduling.enabled 가 없으면(로컬 기본) SchedulerConfig 자체가 뜨지 않아야 한다.
 */
class SchedulerConfigTest {

	// SchedulerConfig 가 리더 리스 서비스를 물게 되면서(둘 다 같은 스위치 조건) 함께 올린다.
	// 리포지토리는 DB 가 필요 없는 목으로 채운다 — 이 테스트의 관심사는 스위치뿐이다.
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(SchedulerLeaseRepository.class, () -> Mockito.mock(SchedulerLeaseRepository.class))
			.withUserConfiguration(SchedulerConfig.class, SchedulerLeaseService.class);

	@Test
	@DisplayName("app.scheduling.enabled 미설정이면 스케줄러 설정이 로드되지 않는다")
	void schedulingDisabledByDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(SchedulerConfig.class);
			assertThat(context).doesNotHaveBean(ScheduledTaskRegistrar.class);
		});
	}

	@Test
	@DisplayName("app.scheduling.enabled=false 면 스케줄러 설정이 로드되지 않는다")
	void schedulingDisabledExplicitly() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(SchedulerConfig.class));
	}

	@Test
	@DisplayName("app.scheduling.enabled=true 면 스케줄러 설정과 TaskScheduler 가 로드된다")
	void schedulingEnabled() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(SchedulerConfig.class);
					assertThat(context).hasBean("taskScheduler");
				});
	}

	@Test
	@DisplayName("fixedDelay 잡 하나가 영영 안 끝나도 다른 fixedDelay 잡은 계속 돈다")
	void 막힌_fixedDelay_잡이_다른_잡을_세우지_않는다() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> {
					TaskScheduler scheduler = context.getBean("taskScheduler", TaskScheduler.class);
					CountDownLatch never = new CountDownLatch(1);
					CountDownLatch otherRuns = new CountDownLatch(3);

					// 2026-11-02: SimpleAsyncTaskScheduler 는 fixedDelay 잡을 스레드 하나에서 돌려서
					// 솔랭 종료 알림이 FCM 에서 멈추자 라이브 폴링까지 22시간 섰다.
					ScheduledFuture<?> stuck = scheduler.scheduleWithFixedDelay(() -> {
						try {
							never.await();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}, Duration.ofMillis(10));
					ScheduledFuture<?> other = scheduler.scheduleWithFixedDelay(otherRuns::countDown, Duration.ofMillis(10));
					try {
						assertThat(otherRuns.await(5, TimeUnit.SECONDS)).isTrue();
					} finally {
						stuck.cancel(true);
						other.cancel(true);
					}
				});
	}
}
