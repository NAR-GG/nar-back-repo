package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Riot API 호출이 트랜잭션 밖에서 일어나는지 지키는 회귀 테스트.
 *
 * 예전엔 pollTrackedAccounts 전체가 @Transactional 이라 계정 수만큼의 Riot 호출·디스코드 웹훅·FCM
 * 발송이 한 트랜잭션에 묶였다. 429(Retry-After 20초) 가 겹치면 커넥션과 행 락을 분 단위로 붙잡아,
 * 2026-07-29 에 member_favorite_player INSERT 등이 락 대기 50초로 80건 타임아웃됐다.
 */
class PlayerSoloRankMonitorTransactionBoundaryTest {

	private final PlayerRiotAccountRepository accountRepository = mock(PlayerRiotAccountRepository.class);
	private final RiotApiClient riotApiClient = mock(RiotApiClient.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private final PlayerSoloRankMonitorService service = new PlayerSoloRankMonitorService(
			accountRepository,
			mock(ChampionDataService.class),
			riotApiClient,
			pacingDisabledProperties(),
			mock(NotificationService.class),
			mock(PlayerSoloRankPushService.class),
			mock(SchedulerAlertService.class),
			mock(SoloRankGameHistoryRecorder.class),
			transactionTemplate);

	@Test
	@DisplayName("Riot 호출이 트랜잭션 개시보다 먼저 끝난다")
	void riot_호출은_트랜잭션_밖에서_수행된다() {
		when(accountRepository.findAllTrackedAccounts()).thenReturn(List.of(trackedAccount()));
		// 진행 중 게임 없음 → markNoRecentMatch 후 persist 만 타는 최단 경로
		when(riotApiClient.getActiveGameByPuuid(anyString(), any())).thenReturn(Optional.empty());

		service.pollTrackedAccounts();

		var order = inOrder(riotApiClient, transactionTemplate);
		order.verify(riotApiClient).getActiveGameByPuuid(anyString(), any());
		order.verify(transactionTemplate).executeWithoutResult(any());
	}

	@Test
	@DisplayName("폴링 메서드에 @Transactional 이 다시 붙지 않았다")
	void 폴링_메서드는_선언적_트랜잭션을_쓰지_않는다() throws NoSuchMethodException {
		assertThat(PlayerSoloRankMonitorService.class.getMethod("pollTrackedAccounts")
				.getAnnotation(Transactional.class))
				.as("@Transactional 이면 Riot·디스코드·FCM 호출이 다시 트랜잭션에 갇힌다")
				.isNull();
	}

	private RiotMonitorProperties pacingDisabledProperties() {
		RiotMonitorProperties properties = new RiotMonitorProperties();
		properties.setMaxRequestsPerSecond(0);
		return properties;
	}

	private PlayerRiotAccount trackedAccount() {
		Player player = Player.builder().name("Faker").build();
		return PlayerRiotAccount.builder()
				.player(player)
				.riotId("Hide on bush#KR1")
				.gameName("Hide on bush")
				.tagLine("KR1")
				.platform("KR")
				.puuid("puuid-1")
				.primaryAccount(true)
				.enabled(true)
				.liveStatus(com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus.OFFLINE)
				.lastCheckedMatchId("KR_111")
				.build();
	}
}
