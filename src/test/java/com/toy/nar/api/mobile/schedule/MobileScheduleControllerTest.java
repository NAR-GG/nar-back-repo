package com.toy.nar.api.mobile.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileScheduleControllerTest {

	private MobileScheduleService mobileScheduleService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mobileScheduleService = mock(MobileScheduleService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MobileScheduleController(mobileScheduleService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void getFiltersReturnsMobileFilterShape() throws Exception {
		when(mobileScheduleService.getFilters("LCK")).thenReturn(new MobileScheduleFilterResponse(
				"LCK",
				List.of(new MobileScheduleFilterResponse.LeagueOption("LCK", "LCK")),
				List.of(new MobileScheduleFilterResponse.TeamOption(1L, "T1", "T1", "https://example.com/t1.png")),
				List.of(new MobileScheduleFilterResponse.SeasonOption(2026, "Spring", "2026 Spring"))));

		mockMvc.perform(get("/api/mobile/schedules/filters"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.defaultLeague").value("LCK"))
				.andExpect(jsonPath("$.leagues[0].code").value("LCK"))
				.andExpect(jsonPath("$.teams[0].teamId").value(1L))
				.andExpect(jsonPath("$.teams[0].teamName").value("T1"))
				.andExpect(jsonPath("$.seasons[0].year").value(2026))
				.andExpect(jsonPath("$.seasons[0].split").value("Spring"))
				.andExpect(jsonPath("$.seasons[0].label").value("2026 Spring"));
	}

	@Test
	void getCalendarReturnsMobileCalendarShape() throws Exception {
		when(mobileScheduleService.getCalendar(YearMonth.of(2026, 4), List.of("LCK"), List.of(1L)))
				.thenReturn(new MobileScheduleCalendarResponse(
						"2026-04",
						"LCK",
						1L,
						List.of(new MobileScheduleCalendarResponse.DateSummary(
								"2026-04-01",
								2,
								List.of(new MobileScheduleCalendarResponse.CalendarMatch(
										"match-1",
										"HLE",
										"BRO",
										"HLE",
										"Hanjin Brion",
										"HLE vs BRO"))))));

		mockMvc.perform(get("/api/mobile/schedules/calendar")
						.param("month", "2026-04")
						.param("league", "LCK")
						.param("teamId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value("2026-04"))
				.andExpect(jsonPath("$.league").value("LCK"))
				.andExpect(jsonPath("$.teamId").value(1L))
				.andExpect(jsonPath("$.dates[0].date").value("2026-04-01"))
				.andExpect(jsonPath("$.dates[0].matchCount").value(2))
				.andExpect(jsonPath("$.dates[0].matches[0].matchId").value("match-1"))
				.andExpect(jsonPath("$.dates[0].matches[0].blueTeamCode").value("HLE"))
				.andExpect(jsonPath("$.dates[0].matches[0].redTeamCode").value("BRO"))
				.andExpect(jsonPath("$.dates[0].matches[0].displayText").value("HLE vs BRO"));
	}

	@Test
	void getDailySchedulesReturnsMobileListShape() throws Exception {
		when(mobileScheduleService.getDailySchedules(LocalDate.of(2026, 4, 1), List.of("LCK"), null))
				.thenReturn(new MobileScheduleListResponse(
						"2026-04-01",
						"LCK",
						null,
						List.of(new MobileScheduleListResponse.MobileMatchSummary(
								"match-1",
								"2026-04-01",
								"18:00",
								"unstarted",
								"T1 vs GEN",
								"LCK",
								new MobileScheduleListResponse.MobileTeamResult("T1", "T1", "https://example.com/t1.png", 0),
								new MobileScheduleListResponse.MobileTeamResult("Gen.G", "GEN", "https://example.com/gen.png", 0),
								null,
								List.of(),
								List.of(new MobileScheduleListResponse.MobileGameSummary(1, "game-1", 100L, null, null))))));

		mockMvc.perform(get("/api/mobile/schedules")
						.param("date", "2026-04-01")
						.param("league", "LCK"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-04-01"))
				.andExpect(jsonPath("$.league").value("LCK"))
				.andExpect(jsonPath("$.matches[0].matchId").value("match-1"))
				.andExpect(jsonPath("$.matches[0].date").value("2026-04-01"))
				.andExpect(jsonPath("$.matches[0].blueTeam.teamCode").value("T1"))
				.andExpect(jsonPath("$.matches[0].redTeam.teamName").value("Gen.G"))
				.andExpect(jsonPath("$.matches[0].games[0].gameId").value("game-1"))
				.andExpect(jsonPath("$.matches[0].games[0].recordGameId").value(100L));
	}
}
