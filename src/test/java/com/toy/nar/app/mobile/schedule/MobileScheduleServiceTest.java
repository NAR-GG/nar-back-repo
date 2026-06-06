package com.toy.nar.app.mobile.schedule;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MobileScheduleServiceTest {

	private LeagueMatchRepository leagueMatchRepository;
	private TeamRepository teamRepository;
	private MobileScheduleService service;

	@BeforeEach
	void setUp() {
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		teamRepository = mock(TeamRepository.class);
		service = new MobileScheduleService(leagueMatchRepository, teamRepository);
	}

	@Test
	void getFiltersReturnsDefaultLeagueAndTeamsForSelectedLeague() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findAllByCodeIn(List.of(
				"T1", "HLE", "GEN", "DK", "KT",
				"DNS", "BFX", "NS", "BRO", "KRX"
		))).thenReturn(List.of(t1));

		MobileScheduleFilterResponse response = service.getFilters(null);

		assertThat(response.defaultLeague()).isEqualTo("LCK");
		assertThat(response.leagues()).extracting(MobileScheduleFilterResponse.LeagueOption::code)
				.contains("LCK", "LPL");
		assertThat(response.teams()).singleElement()
				.extracting(MobileScheduleFilterResponse.TeamOption::teamId,
						MobileScheduleFilterResponse.TeamOption::teamName,
						MobileScheduleFilterResponse.TeamOption::teamCode)
				.containsExactly(1L, "T1", "T1");
	}

	@Test
	void getCalendarGroupsLeagueMatchesByKstDate() {
		LocalDateTime startUtc = LocalDateTime.of(2026, 3, 31, 15, 0);
		LocalDateTime endUtc = LocalDateTime.of(2026, 4, 30, 15, 0);
		when(leagueMatchRepository.findMobileMatchesInRange("LCK", startUtc, endUtc))
				.thenReturn(List.of(
						match("match-1", "LCK", LocalDateTime.of(2026, 3, 31, 15, 30), "T1", "GEN", "unstarted"),
						match("match-2", "LCK", LocalDateTime.of(2026, 4, 1, 10, 0), "DK", "HLE", "unstarted")));

		MobileScheduleCalendarResponse response = service.getCalendar(YearMonth.of(2026, 4), "lck", null);

		assertThat(response.month()).isEqualTo("2026-04");
		assertThat(response.league()).isEqualTo("LCK");
		assertThat(response.dates()).hasSize(1);
		assertThat(response.dates()).extracting(MobileScheduleCalendarResponse.DateSummary::date)
				.containsExactly("2026-04-01");
		assertThat(response.dates()).extracting(MobileScheduleCalendarResponse.DateSummary::matchCount)
				.containsExactly(2L);
		assertThat(response.dates().getFirst().matches()).hasSize(2);
		assertThat(response.dates().getFirst().matches()).extracting(MobileScheduleCalendarResponse.CalendarMatch::matchId)
				.containsExactly("match-1", "match-2");
		assertThat(response.dates().getFirst().matches().getFirst().blueTeamCode()).isEqualTo("T1");
		assertThat(response.dates().getFirst().matches().getFirst().redTeamCode()).isEqualTo("GEN");
		assertThat(response.dates().getFirst().matches().getFirst().displayText()).isEqualTo("T1 vs GEN");
	}

	@Test
	void getDailySchedulesUsesTeamFilterWhenTeamIdExists() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		when(leagueMatchRepository.findMobileTeamMatchesInRange(
				"LCK",
				"T1",
				"T1",
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of(match(
						"match-1",
						"LCK",
						LocalDateTime.of(2026, 4, 1, 9, 0),
						"T1",
						"Gen.G",
						"inProgress")));

		MobileScheduleListResponse response = service.getDailySchedules(
				LocalDate.of(2026, 4, 1),
				"LCK",
				1L);

		assertThat(response.date()).isEqualTo("2026-04-01");
		assertThat(response.teamId()).isEqualTo(1L);
		assertThat(response.matches()).singleElement()
				.satisfies(match -> {
					assertThat(match.matchId()).isEqualTo("match-1");
					assertThat(match.scheduledTime()).isEqualTo("18:00");
					assertThat(match.matchStatus()).isEqualTo("inProgress");
					assertThat(match.blueTeam().teamName()).isEqualTo("T1");
					assertThat(match.redTeam().teamName()).isEqualTo("Gen.g");
					assertThat(match.liveStreamUrl()).isEqualTo("https://play.sooplive.co.kr/aflol");
				});
	}

	@Test
	void getDailySchedulesQueriesOneKstDayAsUtcRange() {
		when(leagueMatchRepository.findMobileMatchesInRange(
				"LCK",
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of());

		service.getDailySchedules(LocalDate.of(2026, 4, 1), null, null);

		ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(leagueMatchRepository).findMobileMatchesInRange(
				org.mockito.ArgumentMatchers.eq("LCK"),
				startCaptor.capture(),
				endCaptor.capture());
		assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 3, 31, 15, 0));
		assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 1, 15, 0));
	}

	@Test
	void invalidLeagueThrowsInvalidInput() {
		assertThatThrownBy(() -> service.getDailySchedules(LocalDate.of(2026, 4, 1), "abc", null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verifyNoInteractions(leagueMatchRepository);
	}

	@Test
	void unknownTeamThrowsDataNotFound() {
		when(teamRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getCalendar(YearMonth.of(2026, 4), "LCK", 999L))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.DATA_NOT_FOUND);
	}

	private LeagueMatch match(
			String id,
			String league,
			LocalDateTime matchDate,
			String blueTeam,
			String redTeam,
			String state) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchTitle(blueTeam + " vs " + redTeam)
				.matchDate(matchDate)
				.state(state)
				.blueTeamName(blueTeam)
				.blueTeamCode(blueTeam)
				.blueTeamImageUrl("https://example.com/" + blueTeam + ".png")
				.blueScore(1)
				.redTeamName(redTeam)
				.redTeamCode(redTeam)
				.redTeamImageUrl("https://example.com/" + redTeam + ".png")
				.redScore(0)
				.build();
	}

	private Team team(Long id, String name, String code, String imageUrl) {
		Team team = Team.builder()
				.name(name)
				.code(code)
				.imageUrl(imageUrl)
				.build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}
}
