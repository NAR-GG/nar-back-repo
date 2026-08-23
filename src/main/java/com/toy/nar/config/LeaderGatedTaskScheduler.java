package com.toy.nar.config;

import org.springframework.lang.NonNull;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * 리더인 파드에서만 잡 본문을 실행하는 {@link TaskScheduler} 데코레이터.
 *
 * <p>게이트가 여기 하나인 이유 — {@code @Scheduled} 26개를 개별 수정하면 새 잡을 추가할 때마다
 * 게이트를 기억해야 한다. 모든 잡이 이 스케줄러를 거치므로 여기서 감싸면 빠뜨릴 수 없다.</p>
 *
 * <p>스케줄 등록 자체는 그대로 둔다 — 발화는 되고 <b>본문만</b> 건너뛴다. 그래서 리더가 되는
 * 순간 다음 발화부터 바로 돈다(재등록 불필요). 건너뛸 때 로그는 남기지 않는다: 라이브 폴링이
 * 5초 주기라 대기 파드가 로그를 도배하게 된다. 리더십 전이는 {@link SchedulerLeaseService} 가
 * 남긴다.</p>
 */
class LeaderGatedTaskScheduler implements TaskScheduler {

	private final TaskScheduler delegate;
	private final SchedulerLeaseService lease;

	LeaderGatedTaskScheduler(TaskScheduler delegate, SchedulerLeaseService lease) {
		this.delegate = delegate;
		this.lease = lease;
	}

	private Runnable gated(Runnable task) {
		return () -> {
			if (lease.isLeader()) {
				task.run();
			}
		};
	}

	@Override
	public ScheduledFuture<?> schedule(@NonNull Runnable task, @NonNull Trigger trigger) {
		return delegate.schedule(gated(task), trigger);
	}

	@Override
	@NonNull
	public ScheduledFuture<?> schedule(@NonNull Runnable task, @NonNull Instant startTime) {
		return delegate.schedule(gated(task), startTime);
	}

	@Override
	@NonNull
	public ScheduledFuture<?> scheduleAtFixedRate(
			@NonNull Runnable task, @NonNull Instant startTime, @NonNull Duration period) {
		return delegate.scheduleAtFixedRate(gated(task), startTime, period);
	}

	@Override
	@NonNull
	public ScheduledFuture<?> scheduleAtFixedRate(@NonNull Runnable task, @NonNull Duration period) {
		return delegate.scheduleAtFixedRate(gated(task), period);
	}

	@Override
	@NonNull
	public ScheduledFuture<?> scheduleWithFixedDelay(
			@NonNull Runnable task, @NonNull Instant startTime, @NonNull Duration delay) {
		return delegate.scheduleWithFixedDelay(gated(task), startTime, delay);
	}

	@Override
	@NonNull
	public ScheduledFuture<?> scheduleWithFixedDelay(@NonNull Runnable task, @NonNull Duration delay) {
		return delegate.scheduleWithFixedDelay(gated(task), delay);
	}
}
