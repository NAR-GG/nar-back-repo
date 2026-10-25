package com.toy.nar.app.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository.EndedGame;

class PlayerSoloTierRefreshServiceTest {

	private final PlayerRiotAccountRepository repository = mock(PlayerRiotAccountRepository.class);
	private final RiotApiClient riotApiClient = mock(RiotApiClient.class);
	private final PlayerSoloTierRefreshService service = new PlayerSoloTierRefreshService(repository, riotApiClient);
	private final LocalDateTime now = LocalDateTime.of(2026, 10, 25, 16, 0);
	private final LocalDateTime ended = now.minusMinutes(5);

	@Test
	void LP_반영_뒤로_티어를_못_받은_계정만_한_번씩_갱신한다() {
		PlayerRiotAccount never = account(1L, "puuid-1", null);
		PlayerRiotAccount stale = account(2L, "puuid-2", ended.plusSeconds(30)); // LP 반영 전에 받은 값
		PlayerRiotAccount fresh = account(3L, "puuid-3", ended.plus(PlayerSoloTierRefreshService.LP_SETTLE));
		when(repository.findEndedGames(now.minus(PlayerSoloTierRefreshService.LOOKBACK),
				now.minus(PlayerSoloTierRefreshService.LP_SETTLE)))
				.thenReturn(List.of(new EndedGame(never, ended), new EndedGame(never, ended.minusMinutes(20)),
						new EndedGame(stale, ended), new EndedGame(fresh, ended)));
		when(riotApiClient.findSoloRankEntry(anyString(), anyString()))
				.thenReturn(Optional.of(new RiotLeagueEntryResponse("RANKED_SOLO_5x5", "MASTER", "I", 182, 12, 11)));

		assertThat(service.refreshRecentlyEnded(now)).isEqualTo(2);

		verify(repository).updateSoloTier(eq(1L), eq("MASTER"), eq("I"), eq(182), any());
		verify(repository).updateSoloTier(eq(2L), eq("MASTER"), eq("I"), eq(182), any());
		verify(riotApiClient, times(2)).findSoloRankEntry(anyString(), anyString());
	}

	@Test
	void 한_계정이_실패해도_나머지는_갱신한다() {
		PlayerRiotAccount failing = account(1L, "puuid-1", null);
		PlayerRiotAccount ok = account(2L, "puuid-2", null);
		when(repository.findEndedGames(any(), any()))
				.thenReturn(List.of(new EndedGame(failing, ended), new EndedGame(ok, ended)));
		when(riotApiClient.findSoloRankEntry(eq("puuid-1"), anyString())).thenThrow(new RiotApiException("429", 429));
		when(riotApiClient.findSoloRankEntry(eq("puuid-2"), anyString())).thenReturn(Optional.empty());

		assertThat(service.refreshRecentlyEnded(now)).isEqualTo(1);
		verify(repository).updateSoloTier(eq(2L), eq(null), eq(null), eq(null), any());
	}

	private static PlayerRiotAccount account(Long id, String puuid, LocalDateTime checkedAt) {
		PlayerRiotAccount account = mock(PlayerRiotAccount.class);
		when(account.getId()).thenReturn(id);
		when(account.getPuuid()).thenReturn(puuid);
		when(account.getPlatform()).thenReturn("NA1");
		when(account.getSoloTierCheckedAt()).thenReturn(checkedAt);
		return account;
	}
}
