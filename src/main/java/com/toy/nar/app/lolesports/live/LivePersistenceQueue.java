package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class LivePersistenceQueue {

	private final LiveMinuteSnapshotWriter snapshotWriter;
	private final LiveObjectEventRecorder objectEventRecorder;
	@Qualifier("applicationTaskExecutor")
	private final AsyncTaskExecutor applicationTaskExecutor;

	@Value("${lolesports.live.queue.snapshot-capacity:600}")
	private int snapshotCapacity;

	@Value("${lolesports.live.queue.object-capacity:1200}")
	private int objectCapacity;

	/** 종료 시 남은 큐를 비우는 데 쓸 최대 시간. graceful shutdown 예산(30초) 안에 들어가야 한다. */
	@Value("${lolesports.live.queue.drain-timeout-ms:10000}")
	private long drainTimeoutMs;

	private BlockingQueue<LiveGameState> snapshotQueue;
	private BlockingQueue<ObjectEventTask> objectQueue;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicLong droppedSnapshots = new AtomicLong();
	private final AtomicLong droppedObjectEvents = new AtomicLong();

	@PostConstruct
	void start() {
		snapshotQueue = new ArrayBlockingQueue<>(snapshotCapacity);
		objectQueue = new ArrayBlockingQueue<>(objectCapacity);
		running.set(true);
		applicationTaskExecutor.execute(this::runSnapshotWorker);
		applicationTaskExecutor.execute(this::runObjectWorker);
	}

	@PreDestroy
	void stop() {
		running.set(false);
		drainRemaining();
	}

	/**
	 * 종료 시 남은 큐를 마감 시한 안에서 비운다.
	 *
	 * 예전에는 running=false 만 세팅해 큐에 쌓인 오브젝트 태스크(최대 1200)와 스냅샷(최대 600)을
	 * 그대로 버렸다. 오브젝트 태스크에는 LIVE_EVENT FCM 푸시가 실려 있어, 무중단 배포로 구 컨테이너가
	 * 내려갈 때 그 알림이 통째로 유실됐다(배포 중 "알림이 한동안 안 오다가 몰아서 옴"의 원인).
	 *
	 * 오브젝트 태스크를 먼저 비운다 — 알림이 걸려 있어 사용자 체감에 직접 영향을 준다.
	 */
	void drainRemaining() {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(drainTimeoutMs);
		int objects = 0;
		int snapshots = 0;

		while (System.nanoTime() < deadline) {
			ObjectEventTask task = objectQueue.poll();
			if (task != null) {
				if (drainOne(() -> objectEventRecorder.record(task.activeGame(), task.windowResponse()))) {
					objects++;
				}
				continue;
			}
			LiveGameState state = snapshotQueue.poll();
			if (state != null) {
				if (drainOne(() -> snapshotWriter.write(state))) {
					snapshots++;
				}
				continue;
			}
			break;
		}

		int remaining = objectQueue.size() + snapshotQueue.size();
		if (objects > 0 || snapshots > 0 || remaining > 0) {
			log.info("Live queue drained on shutdown. objects={} snapshots={} remaining={}",
					objects, snapshots, remaining);
		}
	}

	/** 한 건 실패가 나머지 drain 을 막지 않게 삼킨다. */
	private boolean drainOne(Runnable action) {
		try {
			action.run();
			return true;
		} catch (Exception e) {
			log.warn("Live queue drain item failed: {}", e.getMessage());
			return false;
		}
	}

	public void enqueueSnapshot(LiveGameState state) {
		if (state == null) {
			return;
		}
		if (snapshotQueue.offer(state)) {
			return;
		}
		LiveGameState dropped = snapshotQueue.poll();
		if (dropped != null) {
			droppedSnapshots.incrementAndGet();
		}
		if (!snapshotQueue.offer(state)) {
			droppedSnapshots.incrementAndGet();
			log.warn("Live snapshot queue is full. Dropped latest snapshot gameId={} minute={}",
					state.gameId(),
					state.minuteBucketUtc());
		}
	}

	public void enqueueObjectEvents(ActiveLiveGame activeGame, JsonNode windowResponse) {
		if (activeGame == null || windowResponse == null) {
			return;
		}
		boolean accepted = objectQueue.offer(new ObjectEventTask(activeGame, windowResponse));
		if (!accepted) {
			long dropped = droppedObjectEvents.incrementAndGet();
			log.warn("Live object event queue is full. Dropped object task gameId={} droppedCount={}",
					activeGame.gameId(),
					dropped);
		}
	}

	public LiveQueueStats stats() {
		return new LiveQueueStats(
				snapshotQueue == null ? 0 : snapshotQueue.size(),
				objectQueue == null ? 0 : objectQueue.size(),
				droppedSnapshots.get(),
				droppedObjectEvents.get());
	}

	private void runSnapshotWorker() {
		while (running.get()) {
			try {
				LiveGameState state = snapshotQueue.poll(1, TimeUnit.SECONDS);
				if (state != null) {
					snapshotWriter.write(state);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.warn("Live snapshot worker failed: {}", e.getMessage(), e);
			}
		}
	}

	private void runObjectWorker() {
		while (running.get()) {
			try {
				ObjectEventTask task = objectQueue.poll(1, TimeUnit.SECONDS);
				if (task != null) {
					objectEventRecorder.record(task.activeGame(), task.windowResponse());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.warn("Live object worker failed: {}", e.getMessage(), e);
			}
		}
	}

	private record ObjectEventTask(ActiveLiveGame activeGame, JsonNode windowResponse) {
	}

	public record LiveQueueStats(
			int snapshotQueueSize,
			int objectQueueSize,
			long droppedSnapshots,
			long droppedObjectEvents) {
	}
}
