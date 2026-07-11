package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import com.toy.nar.app.schedule.CacheEvictionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LivePollingSchedulerTest {

	@Test
	void removesActiveGameAfterConsecutiveLiveFeedFailures() {
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveObjectEventRecorder objectEventRecorder = mock(LiveObjectEventRecorder.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LiveFrameProcessor liveFrameProcessor = mock(LiveFrameProcessor.class);
		LiveGameMetadataService liveGameMetadataService = mock(LiveGameMetadataService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		CacheEvictionService cacheEvictionService = mock(CacheEvictionService.class);
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService,
				liveStatsClient,
				objectEventRecorder,
				liveStateStore,
				liveFrameProcessor,
				liveGameMetadataService,
				leagueMatchService,
				cacheEvictionService,
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class));
		ReflectionTestUtils.setField(scheduler, "maxConsecutiveFailures", 2);
		liveStateStore.getActiveGames().put("game-1", new ActiveLiveGame(
				"game-1",
				"match-1",
				"LCK",
				"KT",
				"HLE",
				LocalDateTime.now(ZoneOffset.UTC),
				1));
		when(liveStatsClient.getWindow(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));

		scheduler.pollActiveGames();

		assertThat(liveStateStore.getActiveGames()).doesNotContainKey("game-1");
		verify(objectEventRecorder).evict("game-1");
	}

	@Test
	void completedActiveMatchSyncsScheduleAndEvictsCache() {
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveObjectEventRecorder objectEventRecorder = mock(LiveObjectEventRecorder.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LiveFrameProcessor liveFrameProcessor = mock(LiveFrameProcessor.class);
		LiveGameMetadataService liveGameMetadataService = mock(LiveGameMetadataService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		CacheEvictionService cacheEvictionService = mock(CacheEvictionService.class);
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService,
				liveStatsClient,
				objectEventRecorder,
				liveStateStore,
				liveFrameProcessor,
				liveGameMetadataService,
				leagueMatchService,
				cacheEvictionService,
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class));
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		ActiveLiveGame activeGame = new ActiveLiveGame(
				"game-1",
				"match-1",
				"LCK",
				"DK",
				"BRO",
				LocalDateTime.now(ZoneOffset.UTC),
				0);
		liveStateStore.getActiveGames().put(activeGame.gameId(), activeGame);
		MatchResultDto completed = MatchResultDto.builder()
				.matchId("match-1")
				.leagueName("LCK")
				.state("completed")
				.build();
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(completed))
				.build());
		when(leagueMatchService.syncRealtimeMatchStatus(completed, "LCK")).thenReturn(true);

		scheduler.discoverLiveGames();

		verify(leagueMatchService).syncRealtimeMatchStatus(completed, "LCK");
		verify(cacheEvictionService).evictScheduleCaches();
	}

	@Test
	void firesSetEndImmediatelyWhenFrameGameStateFinished() {
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.pollActiveGames();
		scheduler.pollActiveGames();

		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				eq("match-1"),
				eq(2),
				eq("100"),
				eq("200"),
				eq("KT"),
				eq("HLE"));
	}

	@Test
	void doesNotFireSetEndWhileFrameGameStateInGameOrPaused() {
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("in_game"))
				.thenReturn(windowWithGameState("paused"));

		scheduler.pollActiveGames();
		scheduler.pollActiveGames();

		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void rediscoveryDoesNotRefireFrameDetectedSetEnd() {
		// 업스트림 eventDetails 가 종료된 세트를 inProgress 로 방치하는 케이스:
		// 프레임 finished 로 SET_END 발사 후, 디스커버리가 같은 gameId 를 다시 봐도
		// dedup 이 풀려서 재발사되면 안 된다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		WorldsService worldsService = mock(WorldsService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService, worldsService, leagueMatchService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));
		MatchResultDto stillInProgress = MatchResultDto.builder()
				.matchId("match-1")
				.leagueName("LCK")
				.state("inProgress")
				.liveGameIds(List.of("game-1"))
				.gameIds(List.of("game-0", "game-1"))
				.build();
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(stillInProgress))
				.build());

		scheduler.pollActiveGames();
		scheduler.discoverLiveGames();
		scheduler.pollActiveGames();

		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	private ActiveLiveGame lckGame(String gameId) {
		return new ActiveLiveGame(
				gameId,
				"match-1",
				"LCK",
				"KT",
				"HLE",
				LocalDateTime.now(ZoneOffset.UTC),
				0,
				2,
				"100",
				"200");
	}

	private com.fasterxml.jackson.databind.JsonNode windowWithGameState(String gameState) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
					"{\"frames\":[{\"rfc460Timestamp\":\"2026-07-11T10:00:00Z\",\"gameState\":\"in_game\"},"
							+ "{\"rfc460Timestamp\":\"2026-07-11T10:00:10Z\",\"gameState\":\"" + gameState + "\"}]}");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private LiveStatsClient liveStatsClient(LivePollingScheduler scheduler) {
		return (LiveStatsClient) ReflectionTestUtils.getField(scheduler, "liveStatsClient");
	}

	private LivePollingScheduler schedulerWith(LiveStateStore liveStateStore, TeamLiveEventPushService pushService) {
		return schedulerWith(liveStateStore, pushService, mock(WorldsService.class), mock(LeagueMatchService.class));
	}

	private LivePollingScheduler schedulerWith(
			LiveStateStore liveStateStore,
			TeamLiveEventPushService pushService,
			WorldsService worldsService,
			LeagueMatchService leagueMatchService) {
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService,
				mock(LiveStatsClient.class),
				mock(LiveObjectEventRecorder.class),
				liveStateStore,
				mock(LiveFrameProcessor.class),
				liveGameMetadataServiceMock(),
				leagueMatchService,
				mock(CacheEvictionService.class),
				mock(NotificationService.class),
				pushService);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		ReflectionTestUtils.setField(scheduler, "maxConsecutiveFailures", 6);
		ReflectionTestUtils.setField(scheduler, "notificationLeagues", "LCK");
		return scheduler;
	}

	private LiveGameMetadataService liveGameMetadataServiceMock() {
		return mock(LiveGameMetadataService.class);
	}

	@Test
	void nextPollStartsAtFollowingTenSecondWindow() {
		LivePollingScheduler scheduler = new LivePollingScheduler(
				mock(WorldsService.class),
				mock(LiveStatsClient.class),
				mock(LiveObjectEventRecorder.class),
				new LiveStateStore(),
				mock(LiveFrameProcessor.class),
				mock(LiveGameMetadataService.class),
				mock(LeagueMatchService.class),
				mock(CacheEvictionService.class),
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class));

		Instant nextWindow = ReflectionTestUtils.invokeMethod(
				scheduler,
				"nextWindowStart",
				Instant.parse("2026-05-29T12:00:19Z"));

		assertThat(nextWindow).isEqualTo(Instant.parse("2026-05-29T12:00:20Z"));
	}
}
