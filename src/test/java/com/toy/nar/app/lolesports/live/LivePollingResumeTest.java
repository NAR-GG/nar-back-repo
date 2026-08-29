package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.LeagueConfigService;
import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import com.toy.nar.app.schedule.CacheEvictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 재기동한 파드가 "어디까지 봤는지" 를 DB 에서 이어받는다.
 *
 * <p>인메모리({@code LiveStateStore})만 보면 재기동 직후 자기가 어디까지 봤는지 모른다. 그러면
 * 라이브 엣지에서 다시 시작하므로 그 공백의 프레임을 건너뛰고, {@code LiveObjectEventRecorder} 는
 * 기준선이 없어 첫 프레임을 시드만 한다 — 그 구간 킬·오브젝트가 로그도 없이 유실된다.</p>
 *
 * <p>실측 재기동 공백은 38~48초, 엣지 클램프는 50초다. 지금까지 <b>우연히</b> 덮였고 여유가
 * 2~12초뿐이었다.</p>
 */
class LivePollingResumeTest {

	private static final String GAME_ID = "game-1";
	private static final DateTimeFormatter START_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	/**
	 * 엣지 밴드는 {@code [now-MAX_LAG, now-MIN_FEED_AGE]} 다. 폴링이 요청하는 startingTime 은
	 * 항상 이 안으로 클램프된다.
	 *
	 * <p>값을 하드코딩하지 않고 상수에서 읽는 이유 — 2026-08-29 Riot 이 피드의 "너무 최신"
	 * 룰을 20초에서 80초로 조여 상수가 통째로 올라갔을 때, 여기 박아둔 -50/-35/-70 이 전부
	 * 깨졌다. 밴드가 다시 움직여도 의도("밴드 안으로 클램프된다")는 그대로여야 한다.
	 */
	private static final long MAX_LAG = (long) ReflectionTestUtils.getField(
			LivePollingScheduler.class, "MAX_LAG_SECONDS");
	private static final long MIN_FEED_AGE = (long) ReflectionTestUtils.getField(
			LivePollingScheduler.class, "MIN_FEED_AGE_SECONDS");

	/** 요청한 window 가 엣지 밴드 안인가. 10초 내림과 테스트 실행 시간만큼 여유를 준다. */
	private void assertWithinEdgeBand(Instant requested) {
		assertThat(requested).isBeforeOrEqualTo(Instant.now().minusSeconds(MIN_FEED_AGE - 2));
		assertThat(requested).isAfter(Instant.now().minusSeconds(MAX_LAG + 20));
	}

	private LiveStatsClient liveStatsClient;
	private LiveStateStore liveStateStore;
	private LiveGameMinuteSnapshotRepository snapshotRepository;
	private LivePollingScheduler scheduler;

	@BeforeEach
	void setUp() {
		liveStatsClient = mock(LiveStatsClient.class);
		liveStateStore = new LiveStateStore();
		snapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
		scheduler = new LivePollingScheduler(
				mock(WorldsService.class), liveStatsClient, mock(LiveObjectEventRecorder.class),
				liveStateStore, snapshotRepository,
				mock(LiveFrameProcessor.class), mock(LiveGameMetadataService.class),
				mock(LeagueMatchService.class), mock(LeagueConfigService.class),
				mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180_000L);
		ReflectionTestUtils.setField(scheduler, "maxConsecutiveFailures", 6);
		liveStateStore.getActiveGames().put(GAME_ID, new ActiveLiveGame(
				GAME_ID, "match-1", "LCK", "T1", "HLE", LocalDateTime.now(ZoneOffset.UTC), 1));
		// window 응답 내용은 이 테스트의 관심사가 아니다 — 요청한 startingTime 만 본다.
		when(liveStatsClient.getWindow(anyString(), anyString())).thenReturn(emptyFrames());
		when(liveStatsClient.getDetails(anyString(), anyString())).thenReturn(emptyFrames());
	}

	@Test
	@DisplayName("인메모리가 비어도 DB 의 마지막 프레임에서 이어받는다 — 엣지 밴드 안이면 그대로 쓴다")
	void resumesFromDbWhenMemoryIsEmpty() {
		// 엣지 밴드 바로 바깥(조금 더 과거)의 프레임. 다음 window 는 그 직후라 밴드 안으로
		// 들어오므로 클램프 없이 그대로 쓰인다 — "DB 에서 이어받았다" 가 관측되는 유일한 구간이다.
		Instant lastFrame = Instant.now().minusSeconds(MAX_LAG + 5);
		when(snapshotRepository.findTopByGameIdOrderByFrameTimestampUtcDesc(GAME_ID))
				.thenReturn(Optional.of(snapshotAt(lastFrame)));

		scheduler.pollActiveGames();

		Instant requested = capturedStartingTime();
		// 마지막 프레임보다 뒤 = 이어받았다는 뜻. 엣지로 점프했다면 이보다 더 뒤가 된다.
		assertThat(requested).isAfter(lastFrame);
		assertWithinEdgeBand(requested);
	}

	@Test
	@DisplayName("DB 도 비어 있으면(처음 보는 게임) 엣지에서 시작한다")
	void startsAtEdgeWhenNothingObservedYet() {
		when(snapshotRepository.findTopByGameIdOrderByFrameTimestampUtcDesc(GAME_ID))
				.thenReturn(Optional.empty());

		scheduler.pollActiveGames();

		assertWithinEdgeBand(capturedStartingTime());
	}

	@Test
	@DisplayName("공백이 길면 DB 를 봤어도 엣지로 점프한다 — catch-up 지연을 막는 의도된 동작")
	void stillJumpsToEdgeAfterLongGap() {
		// 10분 전 프레임. 이어받으면 분 단위 catch-up 이 되어 라이브 엣지가 달아난다
		// (2026-07-29 락 대기 50초 뒤 19분 점프 실사고). 그래서 클램프가 이긴다.
		Instant lastFrame = Instant.now().minusSeconds(600);
		when(snapshotRepository.findTopByGameIdOrderByFrameTimestampUtcDesc(GAME_ID))
				.thenReturn(Optional.of(snapshotAt(lastFrame)));

		scheduler.pollActiveGames();

		assertWithinEdgeBand(capturedStartingTime());
	}

	@Test
	@DisplayName("DB 조회가 실패해도 폴링은 계속한다 — 엣지에서 시작하고 예외를 올리지 않는다")
	void keepsPollingWhenSnapshotLookupFails() {
		when(snapshotRepository.findTopByGameIdOrderByFrameTimestampUtcDesc(GAME_ID))
				.thenThrow(new RuntimeException("db down"));

		scheduler.pollActiveGames();

		assertWithinEdgeBand(capturedStartingTime());
	}

	@Test
	@DisplayName("인메모리가 있으면 DB 를 보지 않는다 — 폴링 중에는 인메모리가 항상 더 신선하다")
	void prefersInMemoryOverDb() {
		LocalDateTime frame = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(45);
		liveStateStore.putLatestState(new com.toy.nar.app.lolesports.live.dto.LiveGameState(
				GAME_ID, "match-1", "LCK", "T1", "HLE",
				frame.withSecond(0), frame, java.util.List.of(), java.util.List.of()));

		scheduler.pollActiveGames();

		org.mockito.Mockito.verify(snapshotRepository, org.mockito.Mockito.never())
				.findTopByGameIdOrderByFrameTimestampUtcDesc(anyString());
	}

	private Instant capturedStartingTime() {
		ArgumentCaptor<String> startingTime = ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(liveStatsClient, org.mockito.Mockito.atLeastOnce())
				.getWindow(eq(GAME_ID), startingTime.capture());
		return LocalDateTime.parse(startingTime.getValue(), START_TIME_FORMATTER)
				.toInstant(ZoneOffset.UTC);
	}

	private LiveGameMinuteSnapshot snapshotAt(Instant frameTimestamp) {
		LocalDateTime frame = LocalDateTime.ofInstant(frameTimestamp, ZoneOffset.UTC);
		LiveGameMinuteSnapshot snapshot = new LiveGameMinuteSnapshot(GAME_ID, frame.withSecond(0));
		// 이 필드는 updateSnapshot 으로만 채워지는데 그 시그니처가 길다 — 읽기 대상 하나만 심는다.
		ReflectionTestUtils.setField(snapshot, "frameTimestampUtc", frame);
		return snapshot;
	}

	private com.fasterxml.jackson.databind.JsonNode emptyFrames() {
		return new ObjectMapper().createObjectNode().set("frames",
				new ObjectMapper().createArrayNode());
	}
}
