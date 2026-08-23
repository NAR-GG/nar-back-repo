package com.toy.nar.config;

import com.toy.nar.domain.sync.repository.SchedulerLeaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 게이트는 스케줄 등록이 아니라 발화된 본문을 막는다 — 리더가 되는 순간
 * 다음 발화부터 재등록 없이 바로 돌아야 한다.
 */
class LeaderGatedTaskSchedulerTest {

	/** 넘긴 태스크를 그 자리에서 실행하는 대역. 발화까지 재현할 필요는 없다. */
	private static class ImmediateScheduler implements TaskScheduler {
		@Override
		public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
			task.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
			task.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
			task.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
			task.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
			task.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
			task.run();
			return null;
		}
	}

	private final AtomicInteger runs = new AtomicInteger();

	private LeaderGatedTaskScheduler gated(boolean leader) {
		SchedulerLeaseService lease =
				new SchedulerLeaseService(mock(SchedulerLeaseRepository.class));
		// leaseEnabled=false 면 항상 리더. 리더가 아닌 상태는 켠 채 갱신을 안 한 것으로 만든다.
		ReflectionTestUtils.setField(lease, "leaseEnabled", !leader);
		return new LeaderGatedTaskScheduler(new ImmediateScheduler(), lease);
	}

	@Test
	@DisplayName("리더면 잡 본문이 돈다 — 모든 schedule 변형에서")
	void runsWhenLeader() {
		LeaderGatedTaskScheduler scheduler = gated(true);

		scheduler.schedule(runs::incrementAndGet, Instant.now());
		scheduler.scheduleAtFixedRate(runs::incrementAndGet, Duration.ofSeconds(1));
		scheduler.scheduleAtFixedRate(runs::incrementAndGet, Instant.now(), Duration.ofSeconds(1));
		scheduler.scheduleWithFixedDelay(runs::incrementAndGet, Duration.ofSeconds(1));
		scheduler.scheduleWithFixedDelay(runs::incrementAndGet, Instant.now(), Duration.ofSeconds(1));

		assertThat(runs.get()).isEqualTo(5);
	}

	@Test
	@DisplayName("리더가 아니면 발화는 되지만 본문은 돌지 않는다")
	void skipsWhenNotLeader() {
		LeaderGatedTaskScheduler scheduler = gated(false);

		scheduler.schedule(runs::incrementAndGet, Instant.now());
		scheduler.scheduleAtFixedRate(runs::incrementAndGet, Duration.ofSeconds(1));
		scheduler.scheduleWithFixedDelay(runs::incrementAndGet, Duration.ofSeconds(1));

		assertThat(runs.get()).isZero();
	}
}
