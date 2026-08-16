package com.toy.nar.app.riot;

import java.util.List;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SoloRankEndNotificationServiceTest {

	private static final int RANKED_SOLO = 420;

	private final RiotApiClient riotApiClient = mock(RiotApiClient.class);
	private final ChampionDataService championDataService = mock(ChampionDataService.class);
	private final PlayerSoloRankPushService pushService = mock(PlayerSoloRankPushService.class);
	private final PlayerSoloRankGameRepository gameRepository = mock(PlayerSoloRankGameRepository.class);
	private final SoloRankEndNotificationProperties properties = new SoloRankEndNotificationProperties();

	private SoloRankEndNotificationService service;

	@BeforeEach
	void setUp() {
		properties.setEnabled(true);
		service = new SoloRankEndNotificationService(
				riotApiClient, championDataService, pushService, gameRepository, properties);
		when(championDataService.findChampionByRiotKey(any())).thenReturn(java.util.Optional.empty());
	}

	@Test
	@DisplayName("전역 플래그가 꺼져 있으면 감지도 조회도 발송도 하지 않는다")
	void 플래그가_꺼져_있으면_아무것도_하지_않는다() {
		properties.setEnabled(false);

		service.onGameEnded(account(), "8292488921");

		assertThat(service.sweep()).isZero();
		verifyNoInteractions(riotApiClient);
		verifyNoInteractions(pushService);
	}

	@Test
	@DisplayName("결과가 나오면 종료 알림을 보내고 대기열에서 뺀다")
	void 결과가_나오면_발송한다() {
		givenFinishedMatch();

		service.onGameEnded(account(), "8292488921");

		assertThat(service.sweep()).isEqualTo(1);
		verify(pushService).notifySubscribersPostGame(any(), eq("8292488921"), any(), any(), anyString(), any());
		verify(gameRepository).markEndNotified(eq(1L), eq("8292488921"), any());

		// 두 번째 스윕에는 남은 대상이 없다.
		assertThat(service.sweep()).isZero();
	}

	/** match-v5 는 게임이 끝나도 곧바로 발행되지 않는다. 그때 알림을 보내면 안 되고 다시 봐야 한다. */
	@Test
	@DisplayName("match-v5 가 아직 없으면 보내지 않고 다음 스윕에서 다시 본다")
	void 아직_발행_전이면_보내지_않는다() {
		when(riotApiClient.getMatch(anyString(), anyString())).thenReturn(null);

		service.onGameEnded(account(), "8292488921");

		assertThat(service.sweep()).isZero();
		verify(pushService, never()).notifySubscribersPostGame(any(), any(), any(), any(), any(), any());

		// 다음 스윕에서 발행됐으면 그때 보낸다.
		givenFinishedMatch();
		assertThat(service.sweep()).isEqualTo(1);
	}

	@Test
	@DisplayName("재시도 상한을 넘기면 포기하고 대기열에서 뺀다")
	void 상한을_넘기면_포기한다() {
		when(riotApiClient.getMatch(anyString(), anyString())).thenReturn(null);
		service.onGameEnded(account(), "8292488921");

		int maxAttempts = (int) ReflectionTestUtils.getField(
				SoloRankEndNotificationService.class, "MAX_ATTEMPTS");
		for (int i = 0; i <= maxAttempts; i++) {
			service.sweep();
		}

		// 포기 후에는 결과가 나와도 더 보지 않는다.
		givenFinishedMatch();
		assertThat(service.sweep()).isZero();
		verify(pushService, never()).notifySubscribersPostGame(any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("솔랭이 아닌 큐는 보내지 않는다")
	void 솔랭이_아니면_보내지_않는다() {
		when(riotApiClient.getMatch(anyString(), anyString()))
				.thenReturn(match(450, 1L)); // ARAM

		service.onGameEnded(account(), "8292488921");

		assertThat(service.sweep()).isZero();
		verify(pushService, never()).notifySubscribersPostGame(any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("이미 종료 알림을 낸 게임은 Riot 을 다시 조회하지 않는다")
	void 이미_보낸_게임은_재조회하지_않는다() {
		when(gameRepository.existsByPlayer_IdAndGameIdAndEndNotifiedAtIsNotNull(1L, "8292488921"))
				.thenReturn(true);

		service.onGameEnded(account(), "8292488921");
		service.sweep();

		verifyNoInteractions(riotApiClient);
		verify(pushService, never()).notifySubscribersPostGame(any(), any(), any(), any(), any(), any());
	}

	private void givenFinishedMatch() {
		when(riotApiClient.getMatch(anyString(), anyString())).thenReturn(match(RANKED_SOLO, 1L));
	}

	private RiotMatchResponse match(int queueId, Long gameEndTimestamp) {
		RiotMatchResponse.Participant participant = new RiotMatchResponse.Participant(
				"puuid-1", 1, true, 4, 2, 8);
		RiotMatchResponse.Info info = new RiotMatchResponse.Info(
				queueId, gameEndTimestamp, List.of(participant));
		return new RiotMatchResponse(new RiotMatchResponse.Metadata("KR_8292488921"), info);
	}

	private PlayerRiotAccount account() {
		Player player = mock(Player.class);
		when(player.getId()).thenReturn(1L);
		when(player.getName()).thenReturn("Faker");

		PlayerRiotAccount account = mock(PlayerRiotAccount.class);
		when(account.getPlayer()).thenReturn(player);
		when(account.getPuuid()).thenReturn("puuid-1");
		when(account.getPlatform()).thenReturn("KR");
		when(account.getGameName()).thenReturn("Hide on bush");
		when(account.getTagLine()).thenReturn("KR1");
		return account;
	}
}
