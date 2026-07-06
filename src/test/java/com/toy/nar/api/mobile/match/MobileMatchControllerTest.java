package com.toy.nar.api.mobile.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchGamesResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchPageResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileMatchControllerTest {

	private MobileScheduleService mobileScheduleService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mobileScheduleService = mock(MobileScheduleService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MobileMatchController(mobileScheduleService))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
				.build();
	}

	@Test
	void getMatchReturnsSingleMatchSummary() throws Exception {
		when(mobileScheduleService.getMatch("match-1"))
				.thenReturn(new MobileScheduleListResponse.MobileMatchSummary(
						"match-1",
						"2026-07-06",
						"17:00",
						"inProgress",
						"T1 vs FUR",
						"MSI",
						new MobileScheduleListResponse.MobileTeamResult("T1", "T1", "https://example.com/t1.png", 1),
						new MobileScheduleListResponse.MobileTeamResult("Furia", "FUR", "https://example.com/fur.png", 0),
						"https://chzzk.naver.com/abc",
						List.of(new MobileScheduleListResponse.MobileStreamLink(
								"chzzk", "치지직", "LCK 공식 채널 · 한국어", "https://chzzk.naver.com/abc")),
						List.of(new MobileScheduleListResponse.MobileGameSummary(1, "game-1", null, "LIVE", null))));

		mockMvc.perform(get("/api/mobile/matches/match-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matchId").value("match-1"))
				.andExpect(jsonPath("$.matchStatus").value("inProgress"))
				.andExpect(jsonPath("$.blueTeam.teamName").value("T1"))
				.andExpect(jsonPath("$.blueTeam.score").value(1))
				.andExpect(jsonPath("$.streamLinks[0].provider").value("chzzk"));
	}

	@Test
	void getMatchesReturnsCursorPageShape() throws Exception {
		when(mobileScheduleService.getMatchPage("LCK", null, null, null, null, 20))
				.thenReturn(new MobileMatchPageResponse(
						"LCK",
						null,
						List.of(new MobileScheduleListResponse.MobileMatchSummary(
								"match-1",
								"2026-04-01",
								"18:00",
								"completed",
								"T1 vs GEN",
								"LCK",
								new MobileScheduleListResponse.MobileTeamResult("T1", "T1", "https://example.com/t1.png", 2),
								new MobileScheduleListResponse.MobileTeamResult("Gen.G", "GEN", "https://example.com/gen.png", 1),
								null,
								List.of(),
								List.of(new MobileScheduleListResponse.MobileGameSummary(1, "game-1", 100L, "ENDED", null)))),
						"cursor-token",
						true));

		mockMvc.perform(get("/api/mobile/matches").param("league", "LCK"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.league").value("LCK"))
				.andExpect(jsonPath("$.matches[0].matchId").value("match-1"))
				.andExpect(jsonPath("$.matches[0].date").value("2026-04-01"))
				.andExpect(jsonPath("$.matches[0].games[0].gameOrder").value(1))
				.andExpect(jsonPath("$.matches[0].games[0].gameId").value("game-1"))
				.andExpect(jsonPath("$.matches[0].games[0].recordGameId").value(100L))
				.andExpect(jsonPath("$.nextCursor").value("cursor-token"))
				.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	void getMatchGamesReturnsGameIdentifiers() throws Exception {
		when(mobileScheduleService.getMatchGames("match-1"))
				.thenReturn(new MobileMatchGamesResponse(
						"match-1",
						List.of(
								new MobileScheduleListResponse.MobileGameSummary(1, "game-1", 100L, "ENDED", "https://youtu.be/vod-1"),
								new MobileScheduleListResponse.MobileGameSummary(2, "game-2", null, null, null))));

		mockMvc.perform(get("/api/mobile/matches/match-1/games"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matchId").value("match-1"))
				.andExpect(jsonPath("$.games[0].gameId").value("game-1"))
				.andExpect(jsonPath("$.games[0].recordGameId").value(100L))
				.andExpect(jsonPath("$.games[0].vodUrl").value("https://youtu.be/vod-1"))
				.andExpect(jsonPath("$.games[1].gameOrder").value(2))
				.andExpect(jsonPath("$.games[1].recordGameId").doesNotExist())
				.andExpect(jsonPath("$.games[1].vodUrl").doesNotExist());
	}
}
