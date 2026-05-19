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
