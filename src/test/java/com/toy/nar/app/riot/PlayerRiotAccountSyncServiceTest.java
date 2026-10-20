package com.toy.nar.app.riot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.riot.dto.PlayerRiotAccountSyncResult;
import com.toy.nar.app.riot.dto.RiotAccountResolveResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRiotAccountSyncServiceTest {

	private final PlayerRepository playerRepository = mock(PlayerRepository.class);
	private final PlayerRiotAccountRepository playerRiotAccountRepository = mock(PlayerRiotAccountRepository.class);
	private final RiotApiClient riotApiClient = mock(RiotApiClient.class);
	private final PlatformTransactionManager transactionManager = new NoOpTransactionManager();

	private PlayerRiotAccountSyncService syncService;

	@BeforeEach
	void setUp() {
		RiotMonitorProperties riotMonitorProperties = new RiotMonitorProperties();
		riotMonitorProperties.setTargetLeague("LCK");
		syncService = new PlayerRiotAccountSyncService(
				playerRepository,
				playerRiotAccountRepository,
				riotApiClient,
				riotMonitorProperties,
				new ObjectMapper(),
				transactionManager);
	}

	@Test
	void createsAccountWithLegacySafeEmptySummonerId() {
		Player player = Player.builder()
				.name("Peyz")
				.imageUrl(null)
				.build();
		player.updateProfile(null, null, null, null,
				"[{\"region\":\"KR\",\"riotId\":\"Peyz#KR11 Unranked\"}]");

		when(playerRepository.findSoloRankSyncTargets("LCK")).thenReturn(List.of(player));
		when(playerRiotAccountRepository.findByPlayerIdAndPrimaryAccountTrue(any())).thenReturn(Optional.empty());
		when(riotApiClient.resolveAccountByRiotId("Peyz", "KR11"))
				.thenReturn(new RiotAccountResolveResponse("puuid-1", "Peyz", "KR11"));
		when(playerRiotAccountRepository.saveAndFlush(any(PlayerRiotAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PlayerRiotAccountSyncResult result = syncService.syncPrimaryAccounts();

		ArgumentCaptor<PlayerRiotAccount> captor = ArgumentCaptor.forClass(PlayerRiotAccount.class);
		verify(playerRiotAccountRepository).saveAndFlush(captor.capture());
		assertThat(result.syncedCount()).isEqualTo(1);
		assertThat(captor.getValue().getSummonerId()).isEmpty();
		assertThat(captor.getValue().getRiotId()).isEqualTo("Peyz#KR11");
	}

	@Test
	void addsSecondaryAccountBesidePrimaryAndAlertsFromFirstGame() {
		Player player = Player.builder().name("Faker").imageUrl(null).build();
		when(riotApiClient.resolveAccountByRiotId("Hide in bush", "NYKR"))
				.thenReturn(new RiotAccountResolveResponse("puuid-na", "Hide in bush", "NYKR"));
		when(playerRiotAccountRepository.findByPuuid("puuid-na")).thenReturn(Optional.empty());
		when(playerRiotAccountRepository.saveAndFlush(any(PlayerRiotAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PlayerRiotAccount saved = syncService.addSecondaryAccount(player, "Hide in bush#NYKR", "NA");

		assertThat(saved.isPrimaryAccount()).isFalse();
		assertThat(saved.isEnabled()).isTrue();
		assertThat(saved.getPlatform()).isEqualTo("NA1");
		assertThat(saved.getRiotId()).isEqualTo("Hide in bush#NYKR");
		assertThat(saved.getLastCheckedMatchId()).isEmpty();
		verify(playerRiotAccountRepository, never()).findByPlayerIdAndPrimaryAccountTrue(any());
	}

	@Test
	void rejectsSecondaryAccountAlreadyOwnedByAnotherPlayer() {
		Player owner = Player.builder().name("Oner").imageUrl(null).build();
		ReflectionTestUtils.setField(owner, "id", 1L);
		Player other = Player.builder().name("Faker").imageUrl(null).build();
		ReflectionTestUtils.setField(other, "id", 2L);
		PlayerRiotAccount existing = PlayerRiotAccount.builder()
				.player(owner).riotId("worlds#111").gameName("worlds").tagLine("111")
				.platform("NA1").puuid("puuid-na").primaryAccount(false).enabled(true)
				.build();
		when(riotApiClient.resolveAccountByRiotId("worlds", "111"))
				.thenReturn(new RiotAccountResolveResponse("puuid-na", "worlds", "111"));
		when(playerRiotAccountRepository.findByPuuid("puuid-na")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> syncService.addSecondaryAccount(other, "worlds#111", "NA"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("다른 선수에 등록된 계정");
		verify(playerRiotAccountRepository, never()).saveAndFlush(any());
	}

	@Test
	void failsFastWhenRiotApiIsNotConfigured() {
		doThrow(new RiotApiException(
				"Riot API is not configured. Set RIOT_API_ENABLED=true and provide RIOT_API_KEY.",
				500))
				.when(riotApiClient).assertConfigured();

		assertThatThrownBy(() -> syncService.syncPrimaryAccounts())
				.isInstanceOf(RiotApiException.class)
				.hasMessageContaining("RIOT_API_ENABLED=true")
				.hasMessageContaining("RIOT_API_KEY");
		verify(playerRepository, never()).findSoloRankSyncTargets(any());
	}

	private static final class NoOpTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}
	}
}
