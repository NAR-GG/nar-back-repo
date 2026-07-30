package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임별 프레임 진전을 추적해 피드 정지를 감지한다.
 *
 * <p>업스트림 livestats 가 세트 종료를 알리지 않고 그냥 멈추는 경우가 있다
 * (2026-07-30 LCK HLE vs DK 2세트: 마지막 프레임 20:44:38 이후 29분간 gameState=in_game 으로
 * 동결, finished 미도착). 종료 판정이 프레임 gameState=finished 하나에만 의존하면 이때 세트가
 * 영원히 LIVE 로 남고 SET_END 도 나가지 않는다.</p>
 *
 * <p>정지는 "종료"의 증거가 아니라 "의심"일 뿐이다 — 퍼즈 중에도 피드가 얼 수 있고, LCK 기술
 * 퍼즈는 수십 분씩 간다. 그래서 이 추적기는 정지 여부만 알려주고, 종료 확정은 호출부가 별도
 * 신호(매치 스코어)와 조합해서 판단한다. 마지막 프레임이 gameState=paused 면 명시적 퍼즈이므로
 * 진전으로 취급해 정지로 세지 않는다.</p>
 */
@Component
public class LiveFrameStallTracker {

	private final Clock clock;
	private final long stallThresholdMs;
	private final Map<String, Progress> progressByGame = new ConcurrentHashMap<>();

	// 생성자가 둘(운영용 + 테스트용 Clock 주입)이라 @Autowired 로 스프링이 쓸 쪽을 지정해야 한다.
	// 없으면 기본 생성자를 찾다 기동 실패한다(2026-07-30 배포 실패 실사례 — 블루그린 헬스체크가 차단).
	@org.springframework.beans.factory.annotation.Autowired
	public LiveFrameStallTracker(
			@Value("${lolesports.live.frame-stall-threshold-ms:180000}") long stallThresholdMs) {
		this(Clock.systemUTC(), stallThresholdMs);
	}

	LiveFrameStallTracker(Clock clock, long stallThresholdMs) {
		this.clock = clock;
		this.stallThresholdMs = stallThresholdMs;
	}

	/**
	 * window 응답을 관측하고, 이 게임의 프레임이 임계 시간 이상 정지해 있는지 돌려준다.
	 * 프레임이 없으면(픽밴 등 시작 전) 정지로 보지 않는다.
	 */
	public boolean observeAndCheckStalled(String gameId, JsonNode windowResponse) {
		JsonNode frames = windowResponse == null ? null : windowResponse.path("frames");
		if (frames == null || !frames.isArray() || frames.isEmpty()) {
			return false;
		}
		JsonNode last = frames.get(frames.size() - 1);
		String lastTimestamp = last.path("rfc460Timestamp").asText(null);
		boolean paused = "paused".equalsIgnoreCase(last.path("gameState").asText());
		Instant now = clock.instant();

		Progress progress = progressByGame.compute(gameId, (id, previous) -> {
			if (previous == null
					|| paused
					|| lastTimestamp == null
					|| !lastTimestamp.equals(previous.lastFrameTimestamp())) {
				return new Progress(lastTimestamp, now);
			}
			return previous;
		});
		return now.toEpochMilli() - progress.lastAdvanceAt().toEpochMilli() >= stallThresholdMs;
	}

	/** 관측 없이 현재 정지 여부만 조회한다(디스커버리에서 사용). 관측 이력이 없으면 false. */
	public boolean isStalled(String gameId) {
		Progress progress = progressByGame.get(gameId);
		return progress != null
				&& clock.instant().toEpochMilli() - progress.lastAdvanceAt().toEpochMilli() >= stallThresholdMs;
	}

	public void evict(String gameId) {
		progressByGame.remove(gameId);
	}

	private record Progress(String lastFrameTimestamp, Instant lastAdvanceAt) {
	}
}
