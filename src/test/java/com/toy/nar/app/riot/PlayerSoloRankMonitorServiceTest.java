package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerRiotAlertCheckResult;
import com.toy.nar.app.riot.dto.PlayerSoloRankMonitorResult;
import com.toy.nar.app.riot.dto.RiotCurrentGameResponse;
import com.toy.nar.domain.participant.entity.Champion;
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
	private ChampionDataService championDataService;

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
				championDataService,
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
		when(championDataService.findChampionByRiotKey(157))
				.thenReturn(Optional.of(Champion.builder()
					.championNameKr("야스오")
					.championNameEn("Yasuo")
					.imageUrl("https://ddragon.leagueoflegends.com/cdn/15.13.1/img/champion/Yasuo.png")
					.build()));
		when(riotApiClient.getActiveGameByPuuid("puuid"))
				.thenReturn(Optional.of(new RiotCurrentGameResponse(
						222L,
						420,
						List.of(new RiotCurrentGameResponse.RiotCurrentGameParticipantResponse("puuid", 157, "Hide on bush#KR1")))));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isEqualTo(1);
		assertThat(result.rankedSoloCount()).isEqualTo(1);
		assertThat(account.getLastAlertedMatchId()).isEqualTo("222");
		assertThat(account.getLastCheckedMatchId()).isEqualTo("222");
		verify(notificationService).sendPlayerRankedSoloNotification(
				"Faker",
				"Hide on bush#KR1",
				"Hide on bush",
				"KR1",
				"222",
				"야스오",
				"https://ddragon.leagueoflegends.com/cdn/15.13.1/img/champion/Yasuo.png");
	}

	@Test
	void doesNotSendAlertWhenCurrentGameIsAlreadyChecked() {
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
				.lastCheckedMatchId("222")
				.lastAlertedMatchId("222")
				.build();

		when(playerRiotAccountRepository.findTrackedAccountsByPlatform("KR")).thenReturn(List.of(account));
		when(riotApiClient.getActiveGameByPuuid("puuid"))
				.thenReturn(Optional.of(new RiotCurrentGameResponse(
						222L,
						420,
						List.of(new RiotCurrentGameResponse.RiotCurrentGameParticipantResponse("puuid", 157, "Hide on bush#KR1")))));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isZero();
		assertThat(result.unchangedCount()).isEqualTo(1);
		verify(notificationService, never()).sendPlayerRankedSoloNotification(
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
	}

	@Test
	void primesBaselineWithoutSendingAlertOnFirstLivePoll() {
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
		when(riotApiClient.getActiveGameByPuuid("puuid"))
				.thenReturn(Optional.of(new RiotCurrentGameResponse(
						333L,
						420,
						List.of(new RiotCurrentGameResponse.RiotCurrentGameParticipantResponse("puuid", 238, "Hide on bush#KR1")))));

		PlayerSoloRankMonitorResult result = playerSoloRankMonitorService.pollTrackedAccounts();

		assertThat(result.alertsSentCount()).isZero();
		assertThat(result.rankedSoloCount()).isEqualTo(1);
		assertThat(account.getLastCheckedMatchId()).isEqualTo("333");
		verify(notificationService, never()).sendPlayerRankedSoloNotification(
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
	}

	@Test
	void manualAlertCheckSendsDiscordNotificationForRankedSoloGame() {
		when(championDataService.findChampionByRiotKey(777))
				.thenReturn(Optional.of(Champion.builder()
					.championNameKr("요네")
					.championNameEn("Yone")
					.imageUrl("https://ddragon.leagueoflegends.com/cdn/15.13.1/img/champion/Yone.png")
					.build()));
		when(riotApiClient.getActiveGameByPuuid("manual-puuid"))
				.thenReturn(Optional.of(new RiotCurrentGameResponse(
						999L,
						420,
						List.of(new RiotCurrentGameResponse.RiotCurrentGameParticipantResponse("manual-puuid", 777, "ManualUser#KR1")))));

		PlayerRiotAlertCheckResult result = playerSoloRankMonitorService.checkAndSendAlertByPuuid("manual-puuid");

		assertThat(result.currentGameFound()).isTrue();
		assertThat(result.rankedSolo()).isTrue();
		assertThat(result.notificationSent()).isTrue();
		assertThat(result.riotId()).isEqualTo("ManualUser#KR1");
		assertThat(result.championName()).isEqualTo("요네");
		assertThat(result.queueName()).isEqualTo("RANKED_SOLO");
		verify(notificationService).sendPlayerRankedSoloNotification(
				"ManualUser",
				"ManualUser#KR1",
				"ManualUser",
				"KR1",
				"999",
				"요네",
				"https://ddragon.leagueoflegends.com/cdn/15.13.1/img/champion/Yone.png");
	}

	@Test
	void manualAlertCheckIdentifiesArenaWithoutSendingNotification() {
		when(championDataService.findChampionByRiotKey(202))
				.thenReturn(Optional.of(Champion.builder()
					.championNameKr("진")
					.championNameEn("Jhin")
					.imageUrl("https://ddragon.leagueoflegends.com/cdn/15.13.1/img/champion/Jhin.png")
					.build()));
		when(riotApiClient.getActiveGameByPuuid("arena-puuid"))
				.thenReturn(Optional.of(new RiotCurrentGameResponse(
						1234L,
						1700,
						List.of(new RiotCurrentGameResponse.RiotCurrentGameParticipantResponse("arena-puuid", 202, "ArenaUser#KR1")))));

		PlayerRiotAlertCheckResult result = playerSoloRankMonitorService.checkAndSendAlertByPuuid("arena-puuid");

		assertThat(result.currentGameFound()).isTrue();
		assertThat(result.rankedSolo()).isFalse();
		assertThat(result.notificationSent()).isFalse();
		assertThat(result.queueName()).isEqualTo("ARENA");
		assertThat(result.status()).isEqualTo("CURRENT_GAME_IS_ARENA");
		verify(notificationService, never()).sendPlayerRankedSoloNotification(
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				anyString());
	}
}
