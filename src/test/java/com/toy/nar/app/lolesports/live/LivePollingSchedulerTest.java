package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.schedule.CacheEvictionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
				cacheEvictionService);
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
				cacheEvictionService);
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
	void nextPollStartsAtFollowingTenSecondWindow() {
		LivePollingScheduler scheduler = new LivePollingScheduler(
				mock(WorldsService.class),
				mock(LiveStatsClient.class),
				mock(LiveObjectEventRecorder.class),
				new LiveStateStore(),
				mock(LiveFrameProcessor.class),
				mock(LiveGameMetadataService.class),
				mock(LeagueMatchService.class),
				mock(CacheEvictionService.class));

		Instant nextWindow = ReflectionTestUtils.invokeMethod(
				scheduler,
				"nextWindowStart",
				Instant.parse("2026-05-29T12:00:19Z"));

		assertThat(nextWindow).isEqualTo(Instant.parse("2026-05-29T12:00:20Z"));
	}
}
