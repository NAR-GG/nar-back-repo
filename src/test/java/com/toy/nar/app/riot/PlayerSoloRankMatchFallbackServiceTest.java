package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerSoloRankMatchFallbackResult;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerSoloRankMatchFallbackServiceTest {

	@Mock
	private PlayerRiotAccountRepository playerRiotAccountRepository;

	@Mock
	private RiotApiClient riotApiClient;

	@Mock
	private ChampionDataService championDataService;

	@Mock
	private NotificationService notificationService;

	@Mock
	private PlayerSoloRankPushService playerSoloRankPushService;

	@Mock
	private SoloRankGameHistoryRecorder soloRankGameHistoryRecorder;

	@Mock
	private SchedulerAlertService schedulerAlertService;

	private RiotMatchFallbackProperties properties;
	private PlayerSoloRankMatchFallbackService service;

	private PlayerRiotAccount account;

	@BeforeEach
	void setUp() {
		properties = new RiotMatchFallbackProperties();
		service = new PlayerSoloRankMatchFallbackService(
				playerRiotAccountRepository,
				riotApiClient,
				properties,
				championDataService,
				notificationService,
				playerSoloRankPushService,
				soloRankGameHistoryRecorder,
				schedulerAlertService);

		Player player = Player.builder()
				.name("SUPKING")
				.imageUrl(null)
				.build();
		account = PlayerRiotAccount.builder()
				.player(player)
				.riotId("SUPKING#1015")
				.gameName("SUPKING")
				.tagLine("1015")
				.platform("KR")
				.puuid("puuid-supking")
				.primaryAccount(true)
				.enabled(true)
				.build();
	}

	@Test
	void stripsPlatformPrefixFromMatchId() {
		assertThat(SoloRankMatchResultFormatter.normalizeGameId("KR_8292488921"))
				.isEqualTo("8292488921");
		assertThat(SoloRankMatchResultFormatter.normalizeGameId("8292488921"))
				.isEqualTo("8292488921");
	}

	@Test
	void alertsForFreshNewSoloRankGame() {
		when(playerRiotAccountRepository.findAllTrackedAccounts()).thenReturn(List.of(account));
		when(riotApiClient.getRecentSoloRankMatchIdsByPuuid(eq("puuid-supking"), anyInt(), eq("KR")))
				.thenReturn(List.of("KR_100"));
		when(soloRankGameHistoryRecorder.exists(any(), eq("100"))).thenReturn(false);
		when(riotApiClient.getMatch(eq("KR_100"), eq("KR"))).thenReturn(match(
				420, System.currentTimeMillis() - 60_000,
				new RiotMatchResponse.Participant("puuid-supking", 902, true, 4, 2, 8)));
		when(championDataService.findChampionByRiotKey(902)).thenReturn(Optional.empty());
		when(soloRankGameHistoryRecorder.record(any(), eq("100"), any(), any())).thenReturn(true);

		PlayerSoloRankMatchFallbackResult result = service.pollTrackedAccounts();

		assertThat(result.newGameCount()).isEqualTo(1);
		assertThat(result.alertsSentCount()).isEqualTo(1);
		verify(playerSoloRankPushService).notifySubscribersPostGame(
				any(), eq("100"), any(), any(), eq("솔로 랭크로 승리 · 4/2/8"), anyString());
		verify(notificationService).sendPlayerGameNotification(
				eq("SUPKING"), eq("SUPKING#1015"), eq("SUPKING"), eq("1015"),
				eq("100"), eq("솔로 랭크 (경기 종료·폴백)"), any(), any());
	}

	@Test
	void recordsButDoesNotAlertStaleGame() {
		when(playerRiotAccountRepository.findAllTrackedAccounts()).thenReturn(List.of(account));
		when(riotApiClient.getRecentSoloRankMatchIdsByPuuid(anyString(), anyInt(), anyString()))
				.thenReturn(List.of("KR_100"));
		when(soloRankGameHistoryRecorder.exists(any(), eq("100"))).thenReturn(false);
		// 25시간 전 종료 — 신선도(기본 60분) 초과. 첫 가동 백필 시나리오.
		when(riotApiClient.getMatch(eq("KR_100"), anyString())).thenReturn(match(
				420, System.currentTimeMillis() - 25L * 60 * 60 * 1000,
				new RiotMatchResponse.Participant("puuid-supking", 902, false, 1, 5, 9)));
		when(soloRankGameHistoryRecorder.record(any(), eq("100"), any(), any())).thenReturn(true);

		PlayerSoloRankMatchFallbackResult result = service.pollTrackedAccounts();

		assertThat(result.newGameCount()).isEqualTo(1);
		assertThat(result.alertsSentCount()).isZero();
		verify(playerSoloRankPushService, never()).notifySubscribersPostGame(
				any(), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	void skipsAlreadyRecordedGameWithoutMatchDetailCall() {
		when(playerRiotAccountRepository.findAllTrackedAccounts()).thenReturn(List.of(account));
		when(riotApiClient.getRecentSoloRankMatchIdsByPuuid(anyString(), anyInt(), anyString()))
				.thenReturn(List.of("KR_100"));
		// 라이브 모니터가 이미 적재한 게임 — 상세 조회·알림 모두 없어야 한다.
		when(soloRankGameHistoryRecorder.exists(any(), eq("100"))).thenReturn(true);

		PlayerSoloRankMatchFallbackResult result = service.pollTrackedAccounts();

		assertThat(result.newGameCount()).isZero();
		assertThat(result.alertsSentCount()).isZero();
		verify(riotApiClient, never()).getMatch(anyString(), anyString());
	}

	@Test
	void skipsAlertWhenConcurrentPollAlreadyRecorded() {
		when(playerRiotAccountRepository.findAllTrackedAccounts()).thenReturn(List.of(account));
		when(riotApiClient.getRecentSoloRankMatchIdsByPuuid(anyString(), anyInt(), anyString()))
				.thenReturn(List.of("KR_100"));
		when(soloRankGameHistoryRecorder.exists(any(), eq("100"))).thenReturn(false);
		when(riotApiClient.getMatch(eq("KR_100"), anyString())).thenReturn(match(
				420, System.currentTimeMillis(),
				new RiotMatchResponse.Participant("puuid-supking", 902, true, 4, 2, 8)));
		// exists 통과 후 record 시점에 경쟁 폴링이 먼저 적재 — 알림 중복 방지.
		when(soloRankGameHistoryRecorder.record(any(), eq("100"), any(), any())).thenReturn(false);

		PlayerSoloRankMatchFallbackResult result = service.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isZero();
		verify(playerSoloRankPushService, never()).notifySubscribersPostGame(
				any(), anyString(), any(), any(), anyString(), anyString());
	}

	private RiotMatchResponse match(int queueId, long gameEndTimestamp, RiotMatchResponse.Participant participant) {
		return new RiotMatchResponse(
				new RiotMatchResponse.Metadata("KR_100"),
				new RiotMatchResponse.Info(queueId, gameEndTimestamp, List.of(participant)));
	}
}
