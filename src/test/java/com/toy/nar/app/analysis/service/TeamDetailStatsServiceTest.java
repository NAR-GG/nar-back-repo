package com.toy.nar.app.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.analysis.dto.TeamDetailStatsResponse;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

@ExtendWith(MockitoExtension.class)
class TeamDetailStatsServiceTest {

	@Mock
	private GameTeamStatRepository gameTeamStatRepository;

	@InjectMocks
	private TeamDetailStatsService teamDetailStatsService;

	@Test
	@DisplayName("팀 승률 기준으로 상세 지표를 정렬해 반환한다")
	void getTeamDetailStats_sortedByWinRate() {
		when(gameTeamStatRepository.findTeamDetailStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "RED"))
				.thenReturn(List.of(
						new Object[] { 1L, "T1", "T1", "img1", 30L, 20L, 10L, 16.2, 64500.0, 0.8, 2.1, 6.3, 18L, 17L, 16L, 14L, 12L },
						new Object[] { 2L, "GEN", "GEN", "img2", 30L, 21L, 9L, 15.8, 64000.0, 0.9, 2.0, 6.0, 16L, 15L, 14L, 13L, 11L }));
		when(gameTeamStatRepository.findTeamSeriesStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "RED"))
				.thenReturn(List.of(
						new Object[] { 1L, 10L, 7L, 3L },
						new Object[] { 2L, 10L, 8L, 2L }));

		TeamDetailStatsResponse response = teamDetailStatsService.getTeamDetailStats(
				"lck",
				2026,
				"Round 1-2",
				"14.1",
				"red");

		assertThat(response.getItems()).hasSize(2);
		assertThat(response.getItems().get(0).getTeamName()).isEqualTo("GEN");
		assertThat(response.getItems().get(0).getWinRatePct()).isEqualTo(80.0);
		assertThat(response.getItems().get(0).getMatchesPlayed()).isEqualTo(10);
		assertThat(response.getItems().get(0).getSetWins()).isEqualTo(21);
		assertThat(response.getItems().get(0).getFirstBloodRatePct()).isEqualTo(53.3);
		verify(gameTeamStatRepository).findTeamDetailStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "RED");
		verify(gameTeamStatRepository).findTeamSeriesStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "RED");
	}

	@Test
	@DisplayName("조회 데이터가 없으면 예외를 던진다")
	void getTeamDetailStats_noData() {
		when(gameTeamStatRepository.findTeamDetailStatsByFilter(2026, "LCK", null, null, null))
				.thenReturn(List.of());

		assertThatThrownBy(() -> teamDetailStatsService.getTeamDetailStats("LCK", 2026, null, null, "ALL"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("No team detail stats found");
	}
}
