package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 종료 시 큐를 버리지 않고 비우는지 지키는 회귀 테스트.
 *
 * 예전에는 stop() 이 running=false 만 세팅해 큐에 쌓인 오브젝트 태스크(LIVE_EVENT FCM 푸시 포함)를
 * 그대로 버렸고, 무중단 배포 중 구 컨테이너가 내려갈 때 그 알림이 유실됐다.
 */
class LivePersistenceQueueDrainTest {

	private final LiveMinuteSnapshotWriter snapshotWriter = mock(LiveMinuteSnapshotWriter.class);
	private final LiveObjectEventRecorder objectEventRecorder = mock(LiveObjectEventRecorder.class);
	/** 워커를 띄우지 않는 executor — drain 동작만 검증하고 워커와의 경쟁을 배제한다. */
	private final AsyncTaskExecutor noopExecutor = mock(AsyncTaskExecutor.class);

	private LivePersistenceQueue queue;

	@BeforeEach
	void setUp() {
		queue = new LivePersistenceQueue(snapshotWriter, objectEventRecorder, noopExecutor);
		ReflectionTestUtils.setField(queue, "snapshotCapacity", 10);
		ReflectionTestUtils.setField(queue, "objectCapacity", 10);
		ReflectionTestUtils.setField(queue, "drainTimeoutMs", 5000L);
		ReflectionTestUtils.invokeMethod(queue, "start");
	}

	@Test
	@DisplayName("종료 시 큐에 남은 오브젝트 태스크와 스냅샷을 모두 처리한다")
	void 종료_시_큐를_비운다() {
		queue.enqueueObjectEvents(activeGame("GAME-1"), windowResponse());
		queue.enqueueObjectEvents(activeGame("GAME-2"), windowResponse());
		queue.enqueueSnapshot(state("GAME-1"));

		ReflectionTestUtils.invokeMethod(queue, "stop");

		verify(objectEventRecorder, times(2)).record(any(), any());
		verify(snapshotWriter).write(any());
		org.assertj.core.api.Assertions.assertThat(queue.stats().objectQueueSize()).isZero();
		org.assertj.core.api.Assertions.assertThat(queue.stats().snapshotQueueSize()).isZero();
	}

	@Test
	@DisplayName("한 건이 실패해도 나머지 drain 은 계속된다")
	void 실패한_건이_drain을_막지_않는다() {
		doThrow(new RuntimeException("boom")).when(objectEventRecorder).record(any(), any());
		queue.enqueueObjectEvents(activeGame("GAME-1"), windowResponse());
		queue.enqueueSnapshot(state("GAME-1"));

		ReflectionTestUtils.invokeMethod(queue, "stop");

		verify(snapshotWriter).write(any());
	}

	private ActiveLiveGame activeGame(String gameId) {
		return new ActiveLiveGame(gameId, "MATCH-1", "LCK", "T1", "GEN",
				LocalDateTime.now(), 0, 1, "blue-esports-id", "red-esports-id");
	}

	private ObjectNode windowResponse() {
		return JsonNodeFactory.instance.objectNode();
	}

	private LiveGameState state(String gameId) {
		return new LiveGameState(gameId, "MATCH-1", "LCK", "T1", "GEN",
				LocalDateTime.now(), LocalDateTime.now(), List.of(), List.of());
	}
}
