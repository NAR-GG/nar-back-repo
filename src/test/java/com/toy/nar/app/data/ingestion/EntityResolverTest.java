package com.toy.nar.app.data.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.ChampionRepository;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class EntityResolverTest {

	@Mock
	private LeagueRepository leagueRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private PlayerRepository playerRepository;

	@Mock
	private ChampionRepository championRepository;

	@InjectMocks
	private EntityResolver entityResolver;

	@Test
	@DisplayName("악센트만 다른 기존 팀 이름은 새로 생성하지 않는다")
	void resolveEntitiesFromChunk_reusesExistingTeamWhenOnlyAccentDiffers() {
		Team existingTeam = Team.builder()
				.name("Leviatán")
				.code("LEV")
				.build();
		Player existingPlayer = Player.builder()
				.name("Zothve")
				.build();
		League existingLeague = League.builder()
				.leagueName("LTA S")
				.seasonYear(2025)
				.seasonSplit("Split 1")
				.isPlayoffs(false)
				.build();

		when(teamRepository.findAllWithLeagueTeams()).thenReturn(List.of(existingTeam));
		when(playerRepository.findAllByNameInIgnoreCase(anySet())).thenReturn(List.of(existingPlayer));
		when(leagueRepository.findLeaguesWithTeamsByIdentifiers(anySet(), anySet()))
				.thenReturn(List.of(existingLeague));
		when(championRepository.findAll()).thenReturn(List.of());

		entityResolver.initializeCaches();

		GameDataCsvDto dto = new GameDataCsvDto();
		dto.setLeague("LTA S");
		dto.setYear(2025);
		dto.setSplit("Split 1");
		dto.setPlayoffs(0);
		dto.setPlayername("Zothve");
		dto.setTeamname("Leviatan");

		entityResolver.resolveEntitiesFromChunk(List.of(dto));

		assertThat(entityResolver.resolveTeam("Leviatan")).isSameAs(existingTeam);
		verify(teamRepository, never()).findAllByNameInWithLeagueTeams(anySet());
		verify(teamRepository, never()).saveAll(anyList());
	}
}
