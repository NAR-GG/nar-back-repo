package com.toy.nar.app.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.analysis.dto.TeamDashboardResponse;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class TeamDashboardServiceTest {

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private GameTeamStatRepository gameTeamStatRepository;

	@Mock
	private GameParticipantRepository gameParticipantRepository;

	@Mock
	private BanRepository banRepository;

	@InjectMocks
	private TeamDashboardService teamDashboardService;

	@Test
	@DisplayName("4개 섹션 통합 대시보드를 반환한다")
	void getTeamDashboard_returnsAllSections() {
		Team team = mock(Team.class);
		when(team.getId()).thenReturn(1L);
		when(team.getName()).thenReturn("Gen.G");
		when(team.getCode()).thenReturn("GEN");
		when(team.getImageUrl()).thenReturn("team-image");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

		when(gameTeamStatRepository.findTeamDashboardSummary(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(Collections.singletonList(
						new Object[] { 20L, 14L, 6L, 15.8, 64200.0, 2134.0, 0.9, 2.3, 6.1, 11L, 9L, 8L, 7L, 6L }));
		when(gameTeamStatRepository.findTeamDashboardSeriesSummary(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(Collections.singletonList(new Object[] { 8L, 6L, 2L }));

		when(gameParticipantRepository.findTeamPlayerRecords(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(List.of(
						new Object[] { 101L, "Kiin", "p1", "top", 20L, 14L, 60L, 30L, 90L, 3L, 2L, 1L, 71.2, 23.5, 21.0, 28.7, 1.05 },
						new Object[] { 102L, "Canyon", "p2", "jng", 20L, 14L, 55L, 40L, 120L, 4L, 3L, 0L, 68.4, 20.2, 19.8, 36.2, 1.29 }));

		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(List.<Object[]>of(new Object[] { 11L, "렐", "Rell", "c1", 7L }));
		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", "BLUE"))
				.thenReturn(List.<Object[]>of(new Object[] { 11L, "렐", "Rell", "c1", 4L }));
		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", "RED"))
				.thenReturn(List.<Object[]>of(new Object[] { 11L, "렐", "Rell", "c1", 3L }));
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(List.<Object[]>of(new Object[] { 12L, "신짜오", "Xin Zhao", "c2", 9L }));
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", "BLUE"))
				.thenReturn(List.<Object[]>of(new Object[] { 12L, "신짜오", "Xin Zhao", "c2", 5L }));
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, "Round 1-2", "14.1", "RED"))
				.thenReturn(List.<Object[]>of(new Object[] { 12L, "신짜오", "Xin Zhao", "c2", 4L }));

		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(List.of(
						new Object[] { 101L, "Kiin", "p1", "top", 201L, "그웬", "Gwen", "img-gwen", 6L, 4L, 20L, 12L, 18L,
								Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 3, 0, 0)) },
						new Object[] { 101L, "Kiin", "p1", "top", 202L, "레넥톤", "Renekton", "img-renek", 4L, 3L, 14L, 10L, 22L,
								Timestamp.valueOf(LocalDateTime.of(2026, 1, 20, 3, 0, 0)) }));
		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, "Round 1-2", "14.1", "BLUE"))
				.thenReturn(Collections.singletonList(
						new Object[] { 101L, "Kiin", "p1", "top", 201L, "그웬", "Gwen", "img-gwen", 3L, 2L, 10L, 5L, 8L,
								Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 3, 0, 0)) }));
		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, "Round 1-2", "14.1", "RED"))
				.thenReturn(Collections.singletonList(
						new Object[] { 101L, "Kiin", "p1", "top", 202L, "레넥톤", "Renekton", "img-renek", 2L, 1L, 7L, 5L, 10L,
								Timestamp.valueOf(LocalDateTime.of(2026, 1, 20, 3, 0, 0)) }));

		TeamDashboardResponse response = teamDashboardService.getTeamDashboard(
				1L, "LCK", 2026, "Round 1-2", "14.1", "ALL");

		assertThat(response.getTeamId()).isEqualTo(1L);
		assertThat(response.getGameSummary().getMatchesPlayed()).isEqualTo(8);
		assertThat(response.getGameSummary().getAvgGameLengthSeconds()).isEqualTo(2134.0);
		assertThat(response.getPlayerRecords()).hasSize(2);
		assertThat(response.getPlayerRecords().get(0).getFirstKillCount()).isEqualTo(3);
		assertThat(response.getPlayerRecords().get(0).getAvgKillParticipationPct()).isEqualTo(71.2);
		assertThat(response.getPlayerRecords().get(0).getAvgVisionScore()).isEqualTo(28.7);
		assertThat(response.getBannedAgainst().getAll()).hasSize(1);
		assertThat(response.getBannedAgainst().getBlue()).hasSize(1);
		assertThat(response.getBannedAgainst().getRed()).hasSize(1);
		assertThat(response.getBannedByTeam().getAll()).hasSize(1);
		assertThat(response.getBannedByTeam().getBlue()).hasSize(1);
		assertThat(response.getBannedByTeam().getRed()).hasSize(1);
		assertThat(response.getPlayedChampions().getAll()).hasSize(1);
		assertThat(response.getPlayedChampions().getBlue()).hasSize(1);
		assertThat(response.getPlayedChampions().getRed()).hasSize(1);
		assertThat(response.getPlayedChampions().getAll().get(0).getChampions()).hasSize(2);
	}

	@Test
	@DisplayName("밴 통계는 양쪽 모두 TOP5만 반환한다")
	void getTeamDashboard_limitsBanStatsToTop5() {
		Team team = mock(Team.class);
		when(team.getId()).thenReturn(1L);
		when(team.getName()).thenReturn("Gen.G");
		when(team.getCode()).thenReturn("GEN");
		when(team.getImageUrl()).thenReturn("team-image");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

		when(gameTeamStatRepository.findTeamDashboardSummary(1L, "LCK", 2026, null, null, null))
				.thenReturn(Collections.singletonList(
						new Object[] { 20L, 14L, 6L, 15.8, 64200.0, 2134.0, 0.9, 2.3, 6.1, 11L, 9L, 8L, 7L, 6L }));
		when(gameTeamStatRepository.findTeamDashboardSeriesSummary(1L, "LCK", 2026, null, null, null))
				.thenReturn(Collections.singletonList(new Object[] { 8L, 6L, 2L }));

		when(gameParticipantRepository.findTeamPlayerRecords(1L, "LCK", 2026, null, null, null))
				.thenReturn(List.of());
		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, null, null, null))
				.thenReturn(List.of());
		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, null, null, "BLUE"))
				.thenReturn(List.of());
		when(gameParticipantRepository.findTeamPlayedChampions(1L, "LCK", 2026, null, null, "RED"))
				.thenReturn(List.of());

		List<Object[]> banRows = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			banRows.add(new Object[] { 100L + i, "챔프" + i, "Champ" + i, "img" + i, 10L - i });
		}

		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, null, null, null))
				.thenReturn(banRows);
		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, null, null, "BLUE"))
				.thenReturn(banRows);
		when(banRepository.findBansByTeamWithFilters(1L, "LCK", 2026, null, null, "RED"))
				.thenReturn(banRows);
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, null, null, null))
				.thenReturn(banRows);
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, null, null, "BLUE"))
				.thenReturn(banRows);
		when(banRepository.findBansAgainstTeamWithFilters(1L, "LCK", 2026, null, null, "RED"))
				.thenReturn(banRows);

		TeamDashboardResponse response = teamDashboardService.getTeamDashboard(
				1L, "LCK", 2026, null, null, "ALL");

		assertThat(response.getBannedByTeam().getAll()).hasSize(5);
		assertThat(response.getBannedByTeam().getBlue()).hasSize(5);
		assertThat(response.getBannedByTeam().getRed()).hasSize(5);
		assertThat(response.getBannedAgainst().getAll()).hasSize(5);
		assertThat(response.getBannedAgainst().getBlue()).hasSize(5);
		assertThat(response.getBannedAgainst().getRed()).hasSize(5);
	}
}
