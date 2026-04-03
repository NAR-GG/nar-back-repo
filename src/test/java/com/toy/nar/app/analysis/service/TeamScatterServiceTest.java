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

import com.toy.nar.app.analysis.dto.TeamScatterMetric;
import com.toy.nar.app.analysis.dto.TeamScatterResponse;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

@ExtendWith(MockitoExtension.class)
class TeamScatterServiceTest {

	@Mock
	private GameTeamStatRepository gameTeamStatRepository;

	@InjectMocks
	private TeamScatterService teamScatterService;

	@Test
	@DisplayName("KILLS 지표로 팀 스캐터 데이터를 계산한다")
	void getTeamScatterStats_killsMetric() {
		when(gameTeamStatRepository.findTeamScatterStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "BLUE"))
				.thenReturn(List.of(
						new Object[] { 1L, "T1", "T1", "img1", 20L, 14L, 19.0, 16.0, 65000.0, 10.5 },
						new Object[] { 2L, "GEN", "GEN", "img2", 20L, 10L, 17.0, 15.0, 64000.0, 9.0 }));

		TeamScatterResponse response = teamScatterService.getTeamScatterStats(
				"lck",
				2026,
				"Round 1-2",
				"14.1",
				"blue",
				"KILLS");

		assertThat(response.getMetric()).isEqualTo(TeamScatterMetric.KILLS);
		assertThat(response.getPoints()).hasSize(2);
		assertThat(response.getPoints().get(0).getTeamName()).isEqualTo("T1");
		assertThat(response.getPoints().get(0).getXValue()).isEqualTo(16.0);
		assertThat(response.getPoints().get(0).getAvgOverall()).isEqualTo(19.0);
		assertThat(response.getXLeagueAverage()).isEqualTo(15.5);
		assertThat(response.getYLeagueAverage()).isEqualTo(60.0);
		verify(gameTeamStatRepository).findTeamScatterStatsByFilter(2026, "LCK", "Round 1-2", "14.1", "BLUE");
	}

	@Test
	@DisplayName("지원하지 않는 metric 요청 시 예외를 던진다")
	void getTeamScatterStats_invalidMetric() {
		assertThatThrownBy(() -> teamScatterService.getTeamScatterStats("LCK", 2026, null, null, "ALL", "CS"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported metric");
	}
}
