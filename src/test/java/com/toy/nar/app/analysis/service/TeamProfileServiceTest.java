package com.toy.nar.app.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.app.analysis.dto.TeamProfileHeaderResponse;
import com.toy.nar.app.analysis.dto.TeamSocialLinks;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class TeamProfileServiceTest {

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private LeagueMatchRepository leagueMatchRepository;

	@Mock
	private TeamSocialLinksProvider socialLinksProvider;

	@InjectMocks
	private TeamProfileService teamProfileService;

	@Test
	@DisplayName("팀 헤더에서 이전/오늘/다음 순서로 경기 3개를 반환한다")
	void getProfileHeader_returnsPreviousTodayNext() {
		Team team = mock(Team.class);
		when(team.getId()).thenReturn(10L);
		when(team.getName()).thenReturn("Gen.G");
		when(team.getCode()).thenReturn("GEN");
		when(team.getImageUrl()).thenReturn("team-image");
		when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

		LocalDateTime now = LocalDateTime.now();
			List<LeagueMatch> matches = List.of(
					LeagueMatch.builder().id("m1").leagueName("LCK").matchDate(now.minusDays(1)).state("completed")
							.blueTeamCode("HLE").blueTeamName("HLE").redTeamCode("GEN").redTeamName("Gen.G").blueScore(0).redScore(2).build(),
					LeagueMatch.builder().id("m2").leagueName("LCK").matchDate(now.minusHours(1)).state("inProgress")
							.blueTeamCode("GEN").blueTeamName("Gen.G").redTeamCode("T1").redTeamName("T1").blueScore(1).redScore(0).build(),
					LeagueMatch.builder().id("m3").leagueName("LCK").matchDate(now.plusHours(1)).state("unstarted")
							.blueTeamCode("GEN").blueTeamName("Gen.G").redTeamCode("DK").redTeamName("DK").blueScore(0).redScore(0).build(),
					LeagueMatch.builder().id("m4").leagueName("LCK").matchDate(now.plusDays(1)).state("unstarted")
							.blueTeamCode("GEN").blueTeamName("Gen.G").redTeamCode("KT").redTeamName("KT").blueScore(0).redScore(0).build());

		when(leagueMatchRepository.findTeamMatchesInDateRange(
				eq("LCK"), eq("Gen.G"), eq("GEN"), any(), any(), eq(PageRequest.of(0, 40))))
				.thenReturn(matches);
		when(socialLinksProvider.getSocialLinks("GEN")).thenReturn(
				new TeamSocialLinks("https://geng.gg/", "https://www.instagram.com/gengesports/",
						"https://www.youtube.com/@gengesports", "https://x.com/GenG_KR"));

		TeamProfileHeaderResponse response = teamProfileService.getProfileHeader(10L, "LCK");

		assertThat(response.getTeamId()).isEqualTo(10L);
			assertThat(response.getRecentMatches()).hasSize(3);
			assertThat(response.getRecentMatches().stream().map(m -> m.getRelativeLabel()).toList())
					.isEqualTo(Arrays.asList("이전", "오늘", "다음"));
			assertThat(response.getRecentMatches().get(1).getState()).isEqualTo("inProgress");
			assertThat(response.getRecentMatches().get(1).getBlueTeamCode()).isEqualTo("GEN");
			assertThat(response.getRecentMatches().get(1).getRedTeamCode()).isEqualTo("T1");
			assertThat(response.getSocialLinks()).isNotNull();
			assertThat(response.getSocialLinks().homepage()).isEqualTo("https://geng.gg/");
		}

	@Test
	@DisplayName("오늘 경기가 없으면 이전/다음만 반환한다")
	void getProfileHeader_returnsPreviousAndNext_whenNoTodayMatch() {
		Team team = mock(Team.class);
		when(team.getId()).thenReturn(10L);
		when(team.getName()).thenReturn("Gen.G");
		when(team.getCode()).thenReturn("GEN");
		when(team.getImageUrl()).thenReturn("team-image");
		when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

		LocalDateTime now = LocalDateTime.now();
			List<LeagueMatch> matches = List.of(
					LeagueMatch.builder().id("m1").leagueName("LCK").matchDate(now.minusDays(2)).state("completed")
							.blueTeamCode("HLE").blueTeamName("HLE").redTeamCode("GEN").redTeamName("Gen.G").blueScore(0).redScore(2).build(),
					LeagueMatch.builder().id("m2").leagueName("LCK").matchDate(now.plusDays(1)).state("unstarted")
							.blueTeamCode("GEN").blueTeamName("Gen.G").redTeamCode("DK").redTeamName("DK").blueScore(0).redScore(0).build());

		when(leagueMatchRepository.findTeamMatchesInDateRange(
				eq("LCK"), eq("Gen.G"), eq("GEN"), any(), any(), eq(PageRequest.of(0, 40))))
				.thenReturn(matches);

		TeamProfileHeaderResponse response = teamProfileService.getProfileHeader(10L, "LCK");

		assertThat(response.getRecentMatches()).hasSize(2);
		assertThat(response.getRecentMatches().stream().map(m -> m.getRelativeLabel()).toList())
				.isEqualTo(Arrays.asList("이전", "다음"));
	}
}
