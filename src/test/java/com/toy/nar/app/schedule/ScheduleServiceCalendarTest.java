package com.toy.nar.app.schedule;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.schedule.dto.ScheduleCalendarResponseDto;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleServiceCalendarTest {

	private LeagueMatchRepository leagueMatchRepository;
	private TeamRepository teamRepository;
	private ScheduleService scheduleService;

	@BeforeEach
	void setUp() {
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		teamRepository = mock(TeamRepository.class);
		scheduleService = new ScheduleService(
				null,
				null,
				null,
				null,
				null,
				leagueMatchRepository,
				null,
				null,
				teamRepository);
	}

	@Test
	void monthlyCalendarReturnsDisplayMatchesForExistingApi() {
		// match_date 는 UTC 저장이라 KST 2026-04 는 UTC 03-31 15:00 ~ 04-30 14:59:59 다
		when(leagueMatchRepository.findByDateRange(
				LocalDateTime.of(2026, 3, 31, 15, 0, 0),
				LocalDateTime.of(2026, 4, 30, 14, 59, 59)))
				.thenReturn(List.of(
						match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 8, 0), "BRO", "DNS"),
						match("match-2", "LCK", LocalDateTime.of(2026, 4, 1, 10, 0), "HLE", "T1"),
						match("match-3", "LPL", LocalDateTime.of(2026, 4, 2, 8, 0), "BLG", "JDG")));

		ScheduleCalendarResponseDto response = scheduleService.getMonthlyScheduleCalendar(YearMonth.of(2026, 4));

		assertThat(response.month()).isEqualTo("2026-04");
		assertThat(response.dates()).hasSize(2);
		assertThat(response.dates().getFirst().date()).isEqualTo("2026-04-01");
		assertThat(response.dates().getFirst().matchCount()).isEqualTo(2);
		assertThat(response.dates().getFirst().leagues()).containsExactly("LCK");
		assertThat(response.dates().getFirst().matches())
				.extracting(ScheduleCalendarResponseDto.CalendarMatchDto::displayText)
				.containsExactly("BRO vs DNS", "HLE vs T1");
	}

	@Test
	void KST_새벽_경기는_UTC_로_전날이어도_그날_칸에_찍힌다() {
		// LEC 2026-04-02 02:15 KST = 2026-04-01 17:15 UTC.
		// 예전에는 dateKey 를 matchDate.toLocalDate()(=UTC 날짜)로 만들어 04-01 칸에 찍혔다.
		when(leagueMatchRepository.findByDateRange(
				LocalDateTime.of(2026, 3, 31, 15, 0, 0),
				LocalDateTime.of(2026, 4, 30, 14, 59, 59)))
				.thenReturn(List.of(
						match("match-1", "LEC", LocalDateTime.of(2026, 4, 1, 17, 15), "FNC", "G2")));

		ScheduleCalendarResponseDto response = scheduleService.getMonthlyScheduleCalendar(YearMonth.of(2026, 4));

		assertThat(response.dates()).singleElement()
				.satisfies(date -> assertThat(date.date()).isEqualTo("2026-04-02"));
	}

	@Test
	void monthlyCalendarAppliesLeagueAndTeamFilters() {
		Team t1 = team(1L, "T1", "T1");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		// match_date 는 UTC 저장이라 KST 2026-04 는 UTC 03-31 15:00 ~ 04-30 14:59:59 다
		when(leagueMatchRepository.findByDateRange(
				LocalDateTime.of(2026, 3, 31, 15, 0, 0),
				LocalDateTime.of(2026, 4, 30, 14, 59, 59)))
				.thenReturn(List.of(
						match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 8, 0), "BRO", "DNS"),
						match("match-2", "LCK", LocalDateTime.of(2026, 4, 1, 10, 0), "HLE", "T1"),
						match("match-3", "LPL", LocalDateTime.of(2026, 4, 2, 8, 0), "T1", "JDG")));

		ScheduleCalendarResponseDto response = scheduleService.getMonthlyScheduleCalendar(
				YearMonth.of(2026, 4),
				"LCK",
				1L);

		assertThat(response.dates()).singleElement()
				.satisfies(date -> {
					assertThat(date.date()).isEqualTo("2026-04-01");
					assertThat(date.matchCount()).isEqualTo(1);
					assertThat(date.matches()).singleElement()
							.extracting(ScheduleCalendarResponseDto.CalendarMatchDto::displayText)
							.isEqualTo("HLE vs T1");
				});
	}

	@Test
	void invalidLeagueThrowsInvalidInput() {
		assertThatThrownBy(() -> scheduleService.getMonthlyScheduleCalendar(YearMonth.of(2026, 4), "abc", null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	private LeagueMatch match(
			String id,
			String league,
			LocalDateTime matchDate,
			String blueTeam,
			String redTeam) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchDate(matchDate)
				.blueTeamName(blueTeam)
				.blueTeamCode(blueTeam)
				.redTeamName(redTeam)
				.redTeamCode(redTeam)
				.build();
	}

	private Team team(Long id, String name, String code) {
		Team team = Team.builder()
				.name(name)
				.code(code)
				.build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}
}
