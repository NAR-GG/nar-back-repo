package com.toy.nar.app.lolesports.live;

import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveGameMetadataServiceTest {

	@Test
	void resolvesMetadataFromScheduleAndCachesByGameId() {
		WorldsService worldsService = mock(WorldsService.class);
		LeagueMatchGameRepository leagueMatchGameRepository = mock(LeagueMatchGameRepository.class);
		LiveGameMetadataService service = new LiveGameMetadataService(worldsService, leagueMatchGameRepository);
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1")).thenReturn(Optional.empty());
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(MatchResponseWrapper.builder()
				.matches(List.of(MatchResultDto.builder()
						.matchId("match-1")
						.leagueName("LCK")
						.blueTeam(MatchResultDto.TeamInfo.builder().name("kt Rolster").code("KT").build())
						.redTeam(MatchResultDto.TeamInfo.builder().name("Hanwha Life Esports").code("HLE").build())
						.gameIds(List.of("game-1"))
						.build()))
				.build());

		ActiveLiveGame first = service.resolve("game-1").orElseThrow();
		ActiveLiveGame second = service.resolve("game-1").orElseThrow();

		assertThat(first.matchId()).isEqualTo("match-1");
		assertThat(first.leagueName()).isEqualTo("LCK");
		assertThat(first.blueTeamName()).isEqualTo("kt Rolster");
		assertThat(first.redTeamName()).isEqualTo("Hanwha Life Esports");
		assertThat(second).isEqualTo(first);
		verify(worldsService, times(1)).getWorldsMatches(null, "LCK");
	}
}
