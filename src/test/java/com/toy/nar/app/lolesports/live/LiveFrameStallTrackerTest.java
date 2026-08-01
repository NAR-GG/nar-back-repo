package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프레임 정지 감지가 진짜 정지만 잡는지 지키는 회귀 테스트.
 *
 * 실제 사고: 2026-07-30 HLE vs DK 2세트 — 업스트림이 finished 없이 피드를 얼려
 * 마지막 프레임(20:44:38)이 29분간 그대로였고, 앱은 세트를 영원히 LIVE 로 표시했다.
 * 반대로 퍼즈는 정지로 세면 안 된다 — 가짜 SET_END 가 나가고 dedup 이 소진된다.
 */
class LiveFrameStallTrackerTest {

	private static final long THRESHOLD_MS = 180_000L;

	private final MutableClock clock = new MutableClock(Instant.parse("2026-07-30T11:44:38Z"));
	private final LiveFrameStallTracker tracker = new LiveFrameStallTracker(clock, THRESHOLD_MS);

	@Test
	@DisplayName("프레임이 계속 진전하면 정지가 아니다")
	void 진전하는_프레임은_정지가_아니다() {
		assertThat(tracker.observeAndCheckStalled("G1", window("t1", "in_game"))).isFalse();
		clock.advance(Duration.ofMinutes(4));
		// 4분이 지났어도 새 타임스탬프가 왔으므로 진전으로 리셋된다.
		assertThat(tracker.observeAndCheckStalled("G1", window("t2", "in_game"))).isFalse();
	}

	@Test
	@DisplayName("같은 프레임이 임계 시간 이상 반복되면 정지다")
	void 동결된_프레임은_정지다() {
		tracker.observeAndCheckStalled("G1", window("frozen", "in_game"));
		clock.advance(Duration.ofMinutes(2));
		assertThat(tracker.observeAndCheckStalled("G1", window("frozen", "in_game"))).isFalse();
		clock.advance(Duration.ofMinutes(2));
		assertThat(tracker.observeAndCheckStalled("G1", window("frozen", "in_game"))).isTrue();
		assertThat(tracker.isStalled("G1")).isTrue();
	}

	@Test
	@DisplayName("gameState=paused 는 프레임이 멈춰도 정지로 세지 않는다")
	void 퍼즈는_정지가_아니다() {
		tracker.observeAndCheckStalled("G1", window("frozen", "paused"));
		clock.advance(Duration.ofMinutes(10));
		// 10분째 같은 프레임이지만 paused 는 매 관측마다 진전으로 취급된다.
		assertThat(tracker.observeAndCheckStalled("G1", window("frozen", "paused"))).isFalse();
		assertThat(tracker.isStalled("G1")).isFalse();
	}

	@Test
	@DisplayName("프레임이 없으면(픽밴 등) 정지가 아니다")
	void 프레임_없음은_정지가_아니다() {
		ObjectNode empty = JsonNodeFactory.instance.objectNode();
		empty.putArray("frames");
		assertThat(tracker.observeAndCheckStalled("G1", empty)).isFalse();
	}

	@Test
	@DisplayName("evict 하면 관측 이력이 사라진다")
	void 제거하면_정지_판정이_사라진다() {
		tracker.observeAndCheckStalled("G1", window("frozen", "in_game"));
		clock.advance(Duration.ofMinutes(5));
		assertThat(tracker.isStalled("G1")).isTrue();

		tracker.evict("G1");

		assertThat(tracker.isStalled("G1")).isFalse();
	}

	private ObjectNode window(String timestamp, String gameState) {
		ObjectNode root = JsonNodeFactory.instance.objectNode();
		ArrayNode frames = root.putArray("frames");
		ObjectNode frame = frames.addObject();
		frame.put("rfc460Timestamp", timestamp);
		frame.put("gameState", gameState);
		return root;
	}

	/** 테스트에서 시간을 직접 미는 시계. */
	private static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
