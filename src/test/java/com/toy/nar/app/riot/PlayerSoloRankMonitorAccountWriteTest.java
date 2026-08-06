package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 폴링 write가 계정 식별자를 되돌리지 않는지 지키는 회귀 테스트.
 *
 * <p>모니터는 사이클 시작에 계정 전체를 읽고(detached) 계정마다 상태를 커밋한다. 예전엔 그 스냅샷을
 * {@code save}(merge)해서 전체 컬럼을 UPDATE했다. 그래서 사이클 도중 백오피스에서 계정을 교체하면
 * {@code riot_id}·{@code platform}·{@code puuid}가 스냅샷 값으로 롤백됐다 — 2026-08-04에 Loki
 * (853)의 C9loki#kr3/NA1 부착이 옛 Loki#zxc/KR로 되돌아가 NA 솔랭 게임을 감지하지 못했다.
 */
class PlayerSoloRankMonitorAccountWriteTest {

	private static final long ACCOUNT_ID = 78L;

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
	@DisplayName("폴링 write는 DB 현재 행에 라이브 상태만 쓰고 riot_id·platform·puuid는 건드리지 않는다")
	void 폴링_write는_계정_식별자를_되돌리지_않는다() {
		// 사이클 시작 스냅샷: 아직 옛 KR 계정
		PlayerRiotAccount snapshot = account("Loki#zxc", "KR", "puuid-old");
		// 스냅샷 이후 백오피스가 NA 계정으로 교체한 DB 현재 행
		PlayerRiotAccount inDatabase = account("C9loki#kr3", "NA1", "puuid-new");

		when(accountRepository.findAllTrackedAccounts()).thenReturn(List.of(snapshot));
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(inDatabase));
		// 진행 중 게임 없음 → markNoRecentMatch 후 상태 커밋만 타는 최단 경로
		when(riotApiClient.getActiveGameByPuuid(anyString(), any())).thenReturn(Optional.empty());
		executeCallbacksImmediately();

		service.pollTrackedAccounts();

		assertThat(inDatabase.getRiotId()).isEqualTo("C9loki#kr3");
		assertThat(inDatabase.getPlatform()).isEqualTo("NA1");
		assertThat(inDatabase.getPuuid()).isEqualTo("puuid-new");
		// 상태는 정상 반영돼야 한다(그냥 안 쓰는 것과 구분).
		assertThat(inDatabase.getLiveStatus()).isEqualTo(PlayerRiotAccountLiveStatus.OFFLINE);
		assertThat(inDatabase.getLastMatchCheckedAt()).isNotNull();
		// managed 엔티티 변경이라 save 호출 자체가 없어야 한다(merge 하면 다시 전체 컬럼 UPDATE).
		verify(accountRepository, never()).save(any());
	}

	@Test
	@DisplayName("행이 사라진 계정은 조용히 넘어간다")
	void 삭제된_계정은_예외없이_스킵된다() {
		when(accountRepository.findAllTrackedAccounts()).thenReturn(List.of(account("Loki#zxc", "KR", "puuid-old")));
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
		when(riotApiClient.getActiveGameByPuuid(anyString(), any())).thenReturn(Optional.empty());
		executeCallbacksImmediately();

		assertThat(service.pollTrackedAccounts().checkedCount()).isEqualTo(1);
	}

	private void executeCallbacksImmediately() {
		doAnswer(invocation -> {
			invocation.getArgument(0, Consumer.class).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	private RiotMonitorProperties pacingDisabledProperties() {
		RiotMonitorProperties properties = new RiotMonitorProperties();
		properties.setMaxRequestsPerSecond(0);
		return properties;
	}

	private PlayerRiotAccount account(String riotId, String platform, String puuid) {
		PlayerRiotAccount account = PlayerRiotAccount.builder()
				.player(Player.builder().name("Loki").build())
				.riotId(riotId)
				.gameName(riotId.split("#")[0])
				.tagLine(riotId.split("#")[1])
				.platform(platform)
				.puuid(puuid)
				.primaryAccount(true)
				.enabled(true)
				.liveStatus(PlayerRiotAccountLiveStatus.IN_RANKED_SOLO)
				.build();
		ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
		return account;
	}
}
