package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerSoloRankMonitorResult;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerSoloRankMonitorServiceTest {

	@Mock
	private PlayerRiotAccountRepository playerRiotAccountRepository;

	@Mock
	private RiotApiClient riotApiClient;

	@Mock
	private NotificationService notificationService;

	@Mock
	private SchedulerAlertService schedulerAlertService;

	private RiotMonitorProperties riotMonitorProperties;
	private PlayerSoloRankMonitorService playerSoloRankMonitorService;

	@BeforeEach
	void setUp() {
		riotMonitorProperties = new RiotMonitorProperties();
		riotMonitorProperties.setPlatform("KR");
		riotMonitorProperties.setRecentMatchFetchCount(5);
		playerSoloRankMonitorService = new PlayerSoloRankMonitorService(
				playerRiotAccountRepository,
				riotApiClient,
				riotMonitorProperties,
				notificationService,
				schedulerAlertService);
	}

	@Test
	void sendsAlertOnceForNewRankedSoloGame() {
		Player player = Player.builder()
				.name("Faker")
				.imageUrl(null)
				.build();
		PlayerRiotAccount account = PlayerRiotAccount.builder()
				.player(player)
				.riotId("Hide on bush#KR1")
				.gameName("Hide on bush")
				.tagLine("KR1")
				.platform("KR")
				.puuid("puuid")
				.primaryAccount(true)
				.enabled(true)
				.liveStatus(PlayerRiotAccountLiveStatus.OFFLINE)
				.lastCheckedMatchId("KR_111")
				.build();

		when(playerRiotAccountRepository.findTrackedAccountsByPlatform("KR")).thenReturn(List.of(account));
		when(riotApiClient.getRecentMatchIdsByPuuid("puuid", 5))
				.thenReturn(List.of("KR_222", "KR_111"));
		when(riotApiClient.getMatch("KR_222"))
				.thenReturn(new RiotMatchResponse(
						new RiotMatchResponse.Metadata("KR_222"),
						new RiotMatchResponse.Info(420)));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isEqualTo(1);
		assertThat(result.rankedSoloCount()).isEqualTo(1);
		assertThat(account.getLastAlertedMatchId()).isEqualTo("KR_222");
		assertThat(account.getLastCheckedMatchId()).isEqualTo("KR_222");
		verify(notificationService).sendPlayerRankedSoloNotification(
				"Faker",
				"Hide on bush#KR1",
				"Hide on bush",
				"KR1",
				"KR_222");
	}

	@Test
	void doesNotSendAlertWhenLatestMatchIsAlreadyChecked() {
		Player player = Player.builder()
				.name("Faker")
				.imageUrl(null)
				.build();
		PlayerRiotAccount account = PlayerRiotAccount.builder()
				.player(player)
				.riotId("Hide on bush#KR1")
				.gameName("Hide on bush")
				.tagLine("KR1")
				.platform("KR")
				.puuid("puuid")
				.primaryAccount(true)
				.enabled(true)
				.liveStatus(PlayerRiotAccountLiveStatus.IN_RANKED_SOLO)
				.lastCheckedMatchId("KR_222")
				.lastAlertedMatchId("KR_222")
				.build();

		when(playerRiotAccountRepository.findTrackedAccountsByPlatform("KR")).thenReturn(List.of(account));
		when(riotApiClient.getRecentMatchIdsByPuuid("puuid", 5))
				.thenReturn(List.of("KR_222", "KR_111"));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isZero();
		assertThat(result.unchangedCount()).isEqualTo(1);
		verify(notificationService, never()).sendPlayerRankedSoloNotification(
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
	}

	@Test
	void primesBaselineWithoutSendingAlertOnFirstPoll() {
		Player player = Player.builder()
				.name("Faker")
				.imageUrl(null)
				.build();
		PlayerRiotAccount account = PlayerRiotAccount.builder()
				.player(player)
				.riotId("Hide on bush#KR1")
				.gameName("Hide on bush")
				.tagLine("KR1")
				.platform("KR")
				.puuid("puuid")
				.primaryAccount(true)
				.enabled(true)
				.liveStatus(PlayerRiotAccountLiveStatus.OFFLINE)
				.build();

		when(playerRiotAccountRepository.findTrackedAccountsByPlatform("KR")).thenReturn(List.of(account));
		when(riotApiClient.getRecentMatchIdsByPuuid("puuid", 5))
				.thenReturn(List.of("KR_333"));
		when(riotApiClient.getMatch("KR_333"))
				.thenReturn(new RiotMatchResponse(
						new RiotMatchResponse.Metadata("KR_333"),
						new RiotMatchResponse.Info(420)));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isZero();
		assertThat(result.rankedSoloCount()).isEqualTo(1);
		assertThat(account.getLastCheckedMatchId()).isEqualTo("KR_333");
		verify(notificationService, never()).sendPlayerRankedSoloNotification(
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
	}
}
