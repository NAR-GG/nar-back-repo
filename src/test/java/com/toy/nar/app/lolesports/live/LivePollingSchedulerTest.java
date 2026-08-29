package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.LeagueConfigService;
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
import static org.mockito.ArgumentMatchers.any;
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
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				liveFrameProcessor,
				liveGameMetadataService,
				leagueMatchService,
				mock(LeagueConfigService.class),
				cacheEvictionService,
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
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
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("LCK"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService,
				liveStatsClient,
				objectEventRecorder,
				liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				liveFrameProcessor,
				liveGameMetadataService,
				leagueMatchService,
				leagueConfigService,
				cacheEvictionService,
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
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
	void frameFinishedMarksGameFinishedInStore() {
		// 프레임 finished 는 세트 상태(LIVE/ENDED) 표시의 1차 신호다. store 에 마킹해야
		// 모바일 세트 목록이 stale 3분 잔상 동안 LIVE 로 남지 않는다. 푸시 게이트와 무관하게 마킹.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(false); // 푸시 꺼져 있어도
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.pollActiveGames();

		assertThat(liveStateStore.isFinished("game-1")).isTrue();
	}

	@Test
	void removeGameKeepsFinishedMark() {
		// 종료는 게임 단위의 사실이다. 추적 제거와 함께 지우면 livestats 가 계속 돌려주는 옛
		// 프레임에 재편입돼 끝난 세트가 다시 LIVE 로 뜬다(2026-08-17 KeSPA 2세트: 3세트와 동시 LIVE).
		LiveStateStore liveStateStore = new LiveStateStore();
		liveStateStore.markFinished("game-1");

		liveStateStore.removeGame("game-1");

		assertThat(liveStateStore.getActiveGames()).doesNotContainKey("game-1");
		assertThat(liveStateStore.isFinished("game-1")).isTrue();
	}

	@Test
	void finishedGameIsNotReTrackedByDiscovery() {
		// 세트 종료 후에도 피드는 마지막으로 살아 있던 프레임을 계속 돌려주고 업스트림도 gameId 를
		// liveGameIds 에 남긴다 — 그것으로 재편입되면 끝난 세트가 다시 LIVE 로 뜬다.
		LiveStateStore liveStateStore = new LiveStateStore();
		WorldsService worldsService = mock(WorldsService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore,
				mock(TeamLiveEventPushService.class), worldsService, leagueMatchService);
		liveStateStore.markFinished("game-1");
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
		// 디스커버리 대상 리그는 "추적 중 게임의 리그 + 오늘 ±1일 경기가 있는 리그" 다.
		// 추적 중 게임이 없는 상태를 재현하므로 후자로 LCK 를 넣어야 실제로 순회한다.
		when(leagueMatchService.findLeaguesWithMatchesBetween(any(), any())).thenReturn(List.of("LCK"));

		scheduler.discoverLiveGames();

		assertThat(liveStateStore.getActiveGames()).doesNotContainKey("game-1");
	}

	@Test
	void stalledSetIsFinishedWhenLaterSetIsLive() throws InterruptedException {
		// 업스트림이 세트 상태·스코어를 갱신하지 않는 리그(KeSPA: 종료 후에도 unstarted, 0:0)에서는
		// stall+score 로 종료를 확정할 수 없어 앞 세트가 영구 LIVE 로 남는다. 뒤 세트가 라이브면
		// 앞 세트는 끝난 것이다 — 2026-08-17 T1 vs DNS: 4세트가 도는 동안 2세트가 프레임 19:57 에
		// 멈춘 채 2시간 반 넘게 LIVE.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		// 스코어 합 0 < setNumber(2) — 2차 판정은 성립할 수 없다.
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService,
				new LiveFrameStallTracker(1L), matchRepositoryWithScore(0, 0));
		liveStateStore.getActiveGames().put("game-2", lckGame("game-2"));
		liveStateStore.getActiveGames().put("game-3", laterSetGame("game-3"));
		// 2세트는 같은 프레임에 동결, 3세트는 매 폴마다 진전한다.
		when(liveStatsClient(scheduler).getWindow(eq("game-2"), anyString()))
				.thenReturn(windowAt("2026-08-17T10:57:36Z"));
		when(liveStatsClient(scheduler).getWindow(eq("game-3"), anyString()))
				.thenReturn(windowAt("2026-08-17T13:30:00Z"), windowAt("2026-08-17T13:30:10Z"));

		scheduler.pollActiveGames();
		Thread.sleep(10); // 정지 임계 1ms 를 넘긴다
		scheduler.pollActiveGames();

		assertThat(liveStateStore.isFinished("game-2")).isTrue();
		assertThat(liveStateStore.isFinished("game-3")).isFalse();
		// 지난 세트의 늦은 SET_END 는 쏘지 않는다 — 재기동으로 dedup 이 비면 중복 발송이 된다.
		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void finishedGameDoesNotFireSetStartAgain() {
		// 재편입 방어선. 옛 프레임(in_game)이 다시 관측돼도 이미 끝난 세트면 시작 알림·카드가
		// 나가면 안 된다(2026-08-17 20:42: 20:33 종료된 2세트의 SET_START 가 재발화).
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		liveStateStore.markFinished("game-1");
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("in_game"));

		scheduler.pollActiveGames();

		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_START),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
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

	@Test
	void discoveryFlapDoesNotFireSetEnd() {
		// 픽밴 중 게임이 디스커버리에서 한두 사이클 빠지는 플랩(2026-07-12 MSI 결승 실사례):
		// 즉시 SET_END 를 쏘면 오탐 발송 + DB dedup 키 소진으로 진짜 종료가 무음 스킵된다.
		// stale(3분) 확정 전에는 발사하면 안 된다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		WorldsService worldsService = mock(WorldsService.class);
		LivePollingScheduler scheduler = schedulerWith(
				liveStateStore, pushService, worldsService, mock(LeagueMatchService.class));
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1")); // 방금 본 게임
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of())
				.build());

		scheduler.discoverLiveGames();

		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void staleUndiscoveredGameFiresSetEndFallback() {
		// 프레임 finished 를 못 본 채 게임이 피드에서 사라져 stale(3분) 확정되면 폴백으로 1회 발사.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		WorldsService worldsService = mock(WorldsService.class);
		LivePollingScheduler scheduler = schedulerWith(
				liveStateStore, pushService, worldsService, mock(LeagueMatchService.class));
		liveStateStore.getActiveGames().put("game-1", new ActiveLiveGame(
				"game-1", "match-1", "LCK", "KT", "HLE",
				LocalDateTime.now(ZoneOffset.UTC).minusMinutes(4), 0, 2, "100", "200"));
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of())
				.build());

		scheduler.discoverLiveGames();

		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				eq("match-1"), eq(2), eq("100"), eq("200"), eq("KT"), eq("HLE"));
	}

	@Test
	void upstreamUnstartedButLiveFeedFlipsMatchToInProgress() {
		// EWC 등 업스트림이 라이브 중에도 unstarted 로 방치하는 대회: 시작 시각이 지난 unstarted 경기의
		// livestats 피드가 in_game 이면 state 를 inProgress 로 올리고 라이브로 추적해야 한다.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto ewcUnstarted = MatchResultDto.builder()
				.matchId("ewc-match-1").leagueName("EWC").state("unstarted")
				.matchDate(Instant.now().toString()).gameIds(List.of("ewc-game-1")).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(ewcUnstarted)).build());
		when(liveStatsClient.getWindow(eq("ewc-game-1"), anyString())).thenReturn(windowWithGameState("in_game"));

		scheduler.discoverLiveGames();

		assertThat(ewcUnstarted.getState()).isEqualTo("inProgress");
		verify(leagueMatchService).syncRealtimeMatchStatus(ewcUnstarted, "EWC");
		assertThat(liveStateStore.getActiveGames()).containsKey("ewc-game-1");
	}

	@Test
	void alreadyTrackedUnstartedMatchStaysInProgressNotRevertedByRawState() {
		// 회귀: 라이브로 추적 중(activeMatchIds)인 EWC 경기라도 Riot state 가 unstarted 인 한
		// 매 사이클 피드로 재판정해 inProgress 로 유지해야 한다. 안 그러면 syncRealtimeMatchStatus 가
		// 원본 unstarted 로 DB 를 되돌린다(=schedule 은 unstarted, live/games 만 라이브인 증상).
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		// 이미 추적 중인 상태로 시작
		liveStateStore.getActiveGames().put("ewc-game-1", new ActiveLiveGame(
				"ewc-game-1", "ewc-match-1", "EWC", "GEN", "KC",
				LocalDateTime.now(ZoneOffset.UTC), 0, 1, "100", "200"));
		MatchResultDto ewcUnstarted = MatchResultDto.builder()
				.matchId("ewc-match-1").leagueName("EWC").state("unstarted")
				.matchDate(Instant.now().toString()).gameIds(List.of("ewc-game-1")).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(ewcUnstarted)).build());
		when(liveStatsClient.getWindow(eq("ewc-game-1"), anyString())).thenReturn(windowWithGameState("in_game"));

		scheduler.discoverLiveGames();

		assertThat(ewcUnstarted.getState()).isEqualTo("inProgress");
		verify(leagueMatchService).syncRealtimeMatchStatus(ewcUnstarted, "EWC");
	}

	@Test
	void upstreamUnstartedWithFinishedFeedStaysUnstarted() {
		// 피드 마지막 프레임이 finished(직전 세트 종료 잔상)면 라이브로 오판하면 안 된다.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto ewcUnstarted = MatchResultDto.builder()
				.matchId("ewc-match-1").leagueName("EWC").state("unstarted")
				.matchDate(Instant.now().toString()).gameIds(List.of("ewc-game-1")).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(ewcUnstarted)).build());
		when(liveStatsClient.getWindow(eq("ewc-game-1"), anyString())).thenReturn(windowWithGameState("finished"));

		scheduler.discoverLiveGames();

		assertThat(ewcUnstarted.getState()).isEqualTo("unstarted");
		verify(leagueMatchService, never()).syncRealtimeMatchStatus(
				org.mockito.ArgumentMatchers.any(), anyString());
		assertThat(liveStateStore.getActiveGames()).doesNotContainKey("ewc-game-1");
	}

	@Test
	void trackedMatchWithFinishedFeedDoesNotRevertToUnstarted() {
		// 경기 종료 직후: 프로브가 finished 잔상을 보면 inProgress 승격이 끊기는데,
		// matchId 는 아직 activeMatchIds(stale 3분 창)에 있어 Riot 원본 unstarted 가
		// syncRealtimeMatchStatus 로 그대로 들어가 DB 를 inProgress → unstarted 로 되돌렸다.
		// finished 를 봤으면 이 사이클 sync 를 건너뛰어 DB state 를 유지해야 한다.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		// 마지막 세트를 라이브로 추적하던 중이었음
		liveStateStore.getActiveGames().put("ewc-game-1", new ActiveLiveGame(
				"ewc-game-1", "ewc-match-1", "EWC", "GEN", "KC",
				LocalDateTime.now(ZoneOffset.UTC), 0, 3, "100", "200"));
		MatchResultDto ewcUnstarted = MatchResultDto.builder()
				.matchId("ewc-match-1").leagueName("EWC").state("unstarted")
				.matchDate(Instant.now().toString()).gameIds(List.of("ewc-game-1")).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(ewcUnstarted)).build());
		when(liveStatsClient.getWindow(eq("ewc-game-1"), anyString())).thenReturn(windowWithGameState("finished"));

		scheduler.discoverLiveGames();

		assertThat(ewcUnstarted.getState()).isEqualTo("unstarted");
		verify(leagueMatchService, never()).syncRealtimeMatchStatus(
				org.mockito.ArgumentMatchers.any(), anyString());
	}

	@Test
	void recentCompletedMatchSyncsEvenAfterTrackingEnded() {
		// 구멍 B: 업스트림 completed flip 이 stale 3분 창을 넘겨 도착하면(EWC 는 상습),
		// 게임이 이미 store 에서 제거돼 activeMatchIds 가 비어 completed sync 가 스킵됐다
		// → DB 가 30분 cron 까지 inProgress 로 방치. 최근 경기의 completed 는
		// 추적 여부와 무관하게 sync 해야 한다. (upsert 가 무변경이면 skip 하므로 중복 write 없음)
		WorldsService worldsService = mock(WorldsService.class);
		LiveStateStore liveStateStore = new LiveStateStore(); // 추적 종료 상태(비어 있음)
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, mock(LiveStatsClient.class), mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto completed = MatchResultDto.builder()
				.matchId("ewc-match-1").leagueName("EWC").state("completed")
				.matchDate(Instant.now().minusSeconds(3600).toString()).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(completed)).build());

		scheduler.discoverLiveGames();

		verify(leagueMatchService).syncRealtimeMatchStatus(completed, "EWC");
	}

	@Test
	void finishedFeedWithNaverResultFlipsMatchToCompletedOnce() {
		// 업스트림 flip 은 실측 17분+ 늦게 온다(2026-07-27 KESPA T1 vs DNS: 종료 21:40, flip 21:57).
		// 프레임 finished + 네이버 RESULT 면 그 구간을 기다리지 않고 completed 를 확정해야 한다.
		// 확정 후엔 flip 이 올 때까지 매 사이클(10초) 네이버를 다시 찌르지 않는다.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		CacheEvictionService cacheEvictionService = mock(CacheEvictionService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("KESPA"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("KESPA"));
		when(leagueMatchService.syncCompletedMatchFromNaver(
				org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(true);
		// 종료 확정 여부는 이제 프로세스 메모리가 아니라 DB 상태로 판단한다.
		// 첫 사이클엔 아직 completed 가 아니고, 동기화가 성공한 뒤엔 completed 다.
		when(leagueMatchService.findCompletedMatchIds(org.mockito.ArgumentMatchers.any()))
				.thenReturn(java.util.Set.of(), java.util.Set.of("kespa-match-1"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), new LiveStateStore(),
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, cacheEvictionService, mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto kespaUnstarted = MatchResultDto.builder()
				.matchId("kespa-match-1").leagueName("KESPA").state("unstarted")
				.matchDate(Instant.now().minusSeconds(3600).toString())
				.gameIds(List.of("kespa-game-1")).build();
		when(worldsService.getWorldsMatches(null, "KESPA")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(kespaUnstarted)).build());
		when(liveStatsClient.getWindow(eq("kespa-game-1"), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.discoverLiveGames();
		scheduler.discoverLiveGames();

		verify(leagueMatchService, times(1)).syncCompletedMatchFromNaver(kespaUnstarted, "KESPA");
		verify(cacheEvictionService, times(1)).evictScheduleCaches();
		// 업스트림 원본 unstarted 를 그대로 쓰는 경로는 여전히 막혀 있어야 한다(DB 되돌림 방지).
		verify(leagueMatchService, never()).syncRealtimeMatchStatus(
				org.mockito.ArgumentMatchers.any(), anyString());
	}

	@Test
	void finishedFeedWithoutNaverResultRetriesNextCycle() {
		// 세트 사이(네이버가 아직 RESULT 아님)면 확정하지 않고, 다음 사이클에 다시 시도해야 한다.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		CacheEvictionService cacheEvictionService = mock(CacheEvictionService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("KESPA"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("KESPA"));
		when(leagueMatchService.syncCompletedMatchFromNaver(
				org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(false);
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, liveStatsClient, mock(LiveObjectEventRecorder.class), new LiveStateStore(),
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, cacheEvictionService, mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto kespaUnstarted = MatchResultDto.builder()
				.matchId("kespa-match-1").leagueName("KESPA").state("unstarted")
				.matchDate(Instant.now().minusSeconds(3600).toString())
				.gameIds(List.of("kespa-game-1")).build();
		when(worldsService.getWorldsMatches(null, "KESPA")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(kespaUnstarted)).build());
		when(liveStatsClient.getWindow(eq("kespa-game-1"), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.discoverLiveGames();
		scheduler.discoverLiveGames();

		verify(leagueMatchService, times(2)).syncCompletedMatchFromNaver(kespaUnstarted, "KESPA");
		verify(cacheEvictionService, never()).evictScheduleCaches();
	}

	@Test
	void staleCompletedMatchIsNotSynced() {
		// 과거(창 밖) completed 매치까지 매 사이클 sync 하면 페이지 전체가 대상이 된다 — 창 밖은 스킵 유지.
		WorldsService worldsService = mock(WorldsService.class);
		LiveStateStore liveStateStore = new LiveStateStore();
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("EWC"));
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("EWC"));
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService, mock(LiveStatsClient.class), mock(LiveObjectEventRecorder.class), liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class), liveGameMetadataServiceMock(), leagueMatchService,
				leagueConfigService, mock(CacheEvictionService.class), mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		MatchResultDto oldCompleted = MatchResultDto.builder()
				.matchId("ewc-match-old").leagueName("EWC").state("completed")
				.matchDate(Instant.now().minusSeconds(60 * 60 * 24 * 2).toString()).build();
		when(worldsService.getWorldsMatches(null, "EWC")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(oldCompleted)).build());

		scheduler.discoverLiveGames();

		verify(leagueMatchService, never()).syncRealtimeMatchStatus(
				org.mockito.ArgumentMatchers.any(), anyString());
	}

	@Test
	void setStartFiresOnFirstFrameNotOnDiscovery() {
		// 디스커버리 등장 = 픽밴 시작(업스트림이 픽밴 때 inProgress 로 뒤집음)이므로
		// 그 시점에 쏘면 "이전 세트 종료 몇 분 뒤" 오탐. 첫 프레임 도착 때 1회만 쏴야 한다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		WorldsService worldsService = mock(WorldsService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("LCK"));
		LivePollingScheduler scheduler = schedulerWith(
				liveStateStore, pushService, worldsService, leagueMatchService);
		MatchResultDto inProgress = MatchResultDto.builder()
				.matchId("match-1")
				.leagueName("LCK")
				.state("inProgress")
				.blueTeam(MatchResultDto.TeamInfo.builder().name("KT").externalTeamId("100").build())
				.redTeam(MatchResultDto.TeamInfo.builder().name("HLE").externalTeamId("200").build())
				.liveGameIds(List.of("game-1"))
				.gameIds(List.of("game-0", "game-1"))
				.build();
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(inProgress))
				.build());
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("in_game"));

		scheduler.discoverLiveGames();

		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_START),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());

		scheduler.pollActiveGames();
		scheduler.pollActiveGames();

		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_START),
				eq("match-1"), eq(2), eq("100"), eq("200"), eq("KT"), eq("HLE"));
	}

	@Test
	void firstObservedFrameFinishedSkipsSetStart() {
		// 재기동 등으로 첫 관측이 이미 finished 면 시작 알림은 건너뛰고 종료만 쏜다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.pollActiveGames();

		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_START),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void stalledFrameWithConfirmedScoreFiresSetEnd() throws InterruptedException {
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		// 스코어 합 2 = lckGame 의 setNumber(2) 도달 → 종료 확정 가능.
		com.toy.nar.app.lolesports.repository.LeagueMatchRepository matchRepository =
				matchRepositoryWithScore(1, 1);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService,
				new LiveFrameStallTracker(1L), matchRepository);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		// 두 폴 모두 같은 프레임(동결) + gameState=in_game — finished 는 오지 않는다.
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("in_game"));

		scheduler.pollActiveGames();
		Thread.sleep(10); // 임계 1ms 를 넘긴다
		scheduler.pollActiveGames();

		assertThat(liveStateStore.isFinished("game-1")).isTrue();
		verify(pushService, times(1)).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				eq("match-1"), eq(2), eq("100"), eq("200"), eq("KT"), eq("HLE"));
	}

	@Test
	void stalledFrameWithoutScoreDoesNotFireSetEnd() throws InterruptedException {
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		// 스코어 합 1 < setNumber(2) — 퍼즈로 피드가 얼었을 뿐일 수 있다. 절대 쏘면 안 된다.
		com.toy.nar.app.lolesports.repository.LeagueMatchRepository matchRepository =
				matchRepositoryWithScore(1, 0);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService,
				new LiveFrameStallTracker(1L), matchRepository);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("in_game"));

		scheduler.pollActiveGames();
		Thread.sleep(10);
		scheduler.pollActiveGames();

		assertThat(liveStateStore.isFinished("game-1")).isFalse();
		verify(pushService, never()).notifyMatchEvent(
				eq(TeamLiveEventPushService.TYPE_SET_END),
				anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void lateScoreRecoversMatchEndCard() {
		// 세트 종료 시점 DB 스코어가 직전 세트 값이면 카드가 setEnded 로 나가 "다음 세트 준비 중" 으로
		// 고착한다(2026-08-08 DNS vs NS: 카드 21:46:46 / 스코어 2:0 도착 21:47:03).
		// 스코어가 뒤늦게 도착하면 매치 종료 카드를 한 번 더 보내 복구해야 한다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		java.util.concurrent.atomic.AtomicInteger blueScore = new java.util.concurrent.atomic.AtomicInteger(1);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService,
				new LiveFrameStallTracker(180_000L), matchRepositoryWithMutableScore(blueScore));
		var activityPush = liveActivityPushService(scheduler);
		when(activityPush.isEnabled()).thenReturn(true);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.pollActiveGames();          // 스코어 1:0 — 아직 매치 종료 조건 미달
		blueScore.set(2);                     // 네이버 sync 로 2:0 도착
		scheduler.pollActiveGames();

		verify(activityPush, times(1)).notifySetEnd("match-1", 2, 1, 0, false, null);
		verify(activityPush, times(1)).notifySetEnd("match-1", 2, 2, 0, true, "KT");
	}

	@Test
	void matchEndCardIsNotSentTwiceWhenScoreWasAlreadyFresh() {
		// 세트 종료 시점에 이미 스코어가 맞으면 편승 경로가 매치 종료를 보내고,
		// 복구 경로는 같은 매치에 두 번 쏘지 않아야 한다.
		LiveStateStore liveStateStore = new LiveStateStore();
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		java.util.concurrent.atomic.AtomicInteger blueScore = new java.util.concurrent.atomic.AtomicInteger(2);
		LivePollingScheduler scheduler = schedulerWith(liveStateStore, pushService,
				new LiveFrameStallTracker(180_000L), matchRepositoryWithMutableScore(blueScore));
		var activityPush = liveActivityPushService(scheduler);
		when(activityPush.isEnabled()).thenReturn(true);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));

		scheduler.pollActiveGames();
		scheduler.pollActiveGames();

		verify(activityPush, times(1)).notifySetEnd("match-1", 2, 2, 0, true, "KT");
		verify(activityPush, never()).notifySetEnd(
				anyString(), anyInt(), anyInt(), anyInt(), eq(false), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void allTrackedSetsFinishedFlipsMatchToCompletedFromNaver() {
		// 업스트림 state 가 inProgress 인 채로 flip 이 실측 4분 50초~16분 늦게 온다.
		// 추적 세트가 전부 프레임 finished 면 네이버 RESULT 로 먼저 확정해야 한다.
		LiveStateStore liveStateStore = new LiveStateStore();
		WorldsService worldsService = mock(WorldsService.class);
		LeagueMatchService leagueMatchService = mock(LeagueMatchService.class);
		when(leagueMatchService.findLeaguesWithMatchesBetween(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of("LCK"));
		when(leagueMatchService.syncCompletedMatchFromNaver(
				org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(true);
		// 종료 확정 여부는 이제 프로세스 메모리가 아니라 DB 상태로 판단한다.
		// 첫 사이클엔 아직 completed 가 아니고, 동기화가 성공한 뒤엔 completed 다.
		when(leagueMatchService.findCompletedMatchIds(org.mockito.ArgumentMatchers.any()))
				.thenReturn(java.util.Set.of(), java.util.Set.of("match-1"));
		LivePollingScheduler scheduler = schedulerWith(
				liveStateStore, mock(TeamLiveEventPushService.class), worldsService, leagueMatchService);
		liveStateStore.getActiveGames().put("game-1", lckGame("game-1"));
		when(liveStatsClient(scheduler).getWindow(anyString(), anyString()))
				.thenReturn(windowWithGameState("finished"));
		// 폴링이 프레임 finished 를 확정해야 디스커버리가 종료 후보로 본다.
		scheduler.pollActiveGames();

		MatchResultDto inProgress = MatchResultDto.builder()
				.matchId("match-1").leagueName("LCK").state("inProgress")
				.matchDate(Instant.now().minusSeconds(3600).toString())
				.gameIds(List.of("game-1")).liveGameIds(List.of("game-1")).build();
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(inProgress)).build());

		scheduler.discoverLiveGames();
		scheduler.discoverLiveGames();

		// 확정은 1회. 이후 사이클은 업스트림 flip 전까지 네이버를 다시 찌르지 않는다.
		verify(leagueMatchService, times(1)).syncCompletedMatchFromNaver(inProgress, "LCK");
		// 업스트림 원본 inProgress 로 sync 하면 방금 쓴 completed 가 되돌아간다.
		verify(leagueMatchService, never()).syncRealtimeMatchStatus(
				org.mockito.ArgumentMatchers.any(), anyString());
	}

	/** 스코어가 폴링 사이에 바뀌는 상황(네이버 sync 지연)을 흉내낸다. bestOf 3, 승리 조건 2승. */
	private com.toy.nar.app.lolesports.repository.LeagueMatchRepository matchRepositoryWithMutableScore(
			java.util.concurrent.atomic.AtomicInteger blueScore) {
		var match = mock(com.toy.nar.app.lolesports.repository.LeagueMatch.class);
		when(match.getBlueScore()).thenAnswer(invocation -> blueScore.get());
		when(match.getRedScore()).thenReturn(0);
		when(match.getBestOf()).thenReturn(3);
		when(match.getBlueTeamCode()).thenReturn("KT");
		when(match.getRedTeamCode()).thenReturn("HLE");
		var repository = mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class);
		when(repository.findById("match-1")).thenReturn(java.util.Optional.of(match));
		return repository;
	}

	private com.toy.nar.app.mobile.push.LiveActivityPushService liveActivityPushService(
			LivePollingScheduler scheduler) {
		var mock = (com.toy.nar.app.mobile.push.LiveActivityPushService)
				ReflectionTestUtils.getField(scheduler, "liveActivityPushService");
		// 스케줄러가 종료 발송 선점(claim)/조회를 실제 집합 의미로 쓰므로 모의도 그 계약을 따른다 —
		// 기본 스텁(false)이면 복구 경로가 선점 실패로 오인해 발송 자체를 건너뛴다.
		java.util.Set<String> claimed = java.util.concurrent.ConcurrentHashMap.newKeySet();
		when(mock.claimMatchEndPush(anyString()))
				.thenAnswer(inv -> claimed.add(inv.getArgument(0)));
		when(mock.matchEndPushed(anyString()))
				.thenAnswer(inv -> claimed.contains(inv.getArgument(0)));
		return mock;
	}

	private com.toy.nar.app.lolesports.repository.LeagueMatchRepository matchRepositoryWithScore(
			int blueScore, int redScore) {
		var match = mock(com.toy.nar.app.lolesports.repository.LeagueMatch.class);
		when(match.getBlueScore()).thenReturn(blueScore);
		when(match.getRedScore()).thenReturn(redScore);
		var repository = mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class);
		when(repository.findById("match-1")).thenReturn(java.util.Optional.of(match));
		return repository;
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

	/** 프레임 시각을 지정해 진전/정지를 구분할 수 있는 window. */
	private com.fasterxml.jackson.databind.JsonNode windowAt(String frameTimestamp) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
					"{\"frames\":[{\"rfc460Timestamp\":\"" + frameTimestamp
							+ "\",\"gameState\":\"in_game\"}]}");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** lckGame 과 같은 매치의 다음 세트(3세트). */
	private ActiveLiveGame laterSetGame(String gameId) {
		return new ActiveLiveGame(
				gameId, "match-1", "LCK", "KT", "HLE",
				LocalDateTime.now(ZoneOffset.UTC), 0, 3, "100", "200");
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

	/** 정지 감지 테스트용 — 임계값이 짧은 tracker 와 스코어를 주는 매치 리포지토리를 주입한다. */
	private LivePollingScheduler schedulerWith(
			LiveStateStore liveStateStore,
			TeamLiveEventPushService pushService,
			LiveFrameStallTracker frameStallTracker,
			com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository) {
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("LCK"));
		when(leagueConfigService.isNotificationEnabled("LCK")).thenReturn(true);
		LivePollingScheduler scheduler = new LivePollingScheduler(
				mock(WorldsService.class),
				mock(LiveStatsClient.class),
				mock(LiveObjectEventRecorder.class),
				liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class),
				liveGameMetadataServiceMock(),
				mock(LeagueMatchService.class),
				leagueConfigService,
				mock(CacheEvictionService.class),
				mock(NotificationService.class),
				pushService,
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				frameStallTracker,
				leagueMatchRepository,
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		ReflectionTestUtils.setField(scheduler, "maxConsecutiveFailures", 6);
		return scheduler;
	}

	private LivePollingScheduler schedulerWith(
			LiveStateStore liveStateStore,
			TeamLiveEventPushService pushService,
			WorldsService worldsService,
			LeagueMatchService leagueMatchService) {
		LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);
		when(leagueConfigService.liveLeagues()).thenReturn(List.of("LCK"));
		when(leagueConfigService.isNotificationEnabled("LCK")).thenReturn(true);
		LivePollingScheduler scheduler = new LivePollingScheduler(
				worldsService,
				mock(LiveStatsClient.class),
				mock(LiveObjectEventRecorder.class),
				liveStateStore,
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class),
				liveGameMetadataServiceMock(),
				leagueMatchService,
				leagueConfigService,
				mock(CacheEvictionService.class),
				mock(NotificationService.class),
				pushService,
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);
		ReflectionTestUtils.setField(scheduler, "staleThresholdMs", 180000L);
		ReflectionTestUtils.setField(scheduler, "maxConsecutiveFailures", 6);
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
				mock(com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository.class),
				mock(LiveFrameProcessor.class),
				mock(LiveGameMetadataService.class),
				mock(LeagueMatchService.class),
				mock(LeagueConfigService.class),
				mock(CacheEvictionService.class),
				mock(NotificationService.class),
				mock(TeamLiveEventPushService.class),
				mock(com.toy.nar.app.mobile.push.LiveActivityPushService.class),
				mock(com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository.class),
				new LiveFrameStallTracker(180_000L),
				mock(com.toy.nar.app.lolesports.repository.LeagueMatchRepository.class),
				Runnable::run);

		Instant nextWindow = ReflectionTestUtils.invokeMethod(
				scheduler,
				"nextWindowStart",
				Instant.parse("2026-05-29T12:00:19Z"));

		assertThat(nextWindow).isEqualTo(Instant.parse("2026-05-29T12:00:20Z"));
	}

	@Test
	void 매치_종료_판정은_다전제_승리조건을_따른다() {
		// Live Activity 카드를 end 로 내릴지 가르는 판정이라, 틀리면 경기 도중에 카드가 사라진다.
		assertThat(LivePollingScheduler.isMatchEnded(1, 1, 0)).isTrue();
		assertThat(LivePollingScheduler.isMatchEnded(3, 2, 0)).isTrue();
		assertThat(LivePollingScheduler.isMatchEnded(3, 1, 1)).isFalse();
		assertThat(LivePollingScheduler.isMatchEnded(5, 2, 2)).isFalse();
		assertThat(LivePollingScheduler.isMatchEnded(5, 3, 2)).isTrue();
	}

	@Test
	void bestOf_를_모르면_매치_종료로_단정하지_않는다() {
		// 모르는 채로 종료를 보내면 카드가 경기 중에 내려간다. 모를 땐 유지가 안전하다.
		assertThat(LivePollingScheduler.isMatchEnded(null, 3, 0)).isFalse();
		assertThat(LivePollingScheduler.isMatchEnded(0, 3, 0)).isFalse();
	}

	@Test
	void 스코어가_null_이면_0으로_보고_종료로_보지_않는다() {
		assertThat(LivePollingScheduler.isMatchEnded(3, null, null)).isFalse();
	}

	/**
	 * 2026-08-29 라이브 전면 중단의 회귀 테스트.
	 *
	 * <p>Riot 이 "window end-time 이 80초보다 최신이면 거부" 로 룰을 조였는데(그 전엔 20초)
	 * 우리는 40~50초 전 window 를 요청하고 있었다. 진행 중이던 경기가 전부 매 틱 400 이었다.
	 *
	 * <p>window 는 {@code [start, start+10초]} 라 start 가 90초보다 오래돼야 end 가 80초를 넘는다.
	 * 상수를 다시 낮추면 라이브가 통째로 죽으므로 여기서 잠근다.
	 */
	@Test
	void 요청하는_window_는_피드의_80초_룰보다_오래된_구간이어야_한다() {
		long minFeedAge = (long) ReflectionTestUtils.getField(
				LivePollingScheduler.class, "MIN_FEED_AGE_SECONDS");
		long maxLag = (long) ReflectionTestUtils.getField(
				LivePollingScheduler.class, "MAX_LAG_SECONDS");

		// start + 10초(window 길이) 가 80초보다 오래돼야 한다.
		assertThat(minFeedAge).isGreaterThanOrEqualTo(90L);

		// 클램프가 바닥(MAX_LAG) → 천장(MIN_FEED_AGE) 순서다. 바닥이 더 최신이면 천장이 도로
		// 끌어내려 MAX_LAG 가 죽은 상수가 된다 — 실제로 그런 적이 있다.
		assertThat(maxLag).isGreaterThan(minFeedAge);
	}
}
