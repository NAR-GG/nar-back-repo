package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class NaverStandingsClientTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 2026-09-24 실측 모양 — 정규 시즌, 하루짜리 이벤트, 아직 안 열린 다음 시즌, 다른 리그. */
	private static JsonNode leagues() throws Exception {
		return MAPPER.readTree("""
				[
				  {"gameCode":"lol","topLeagueId":"lck","leagueId":"lck_2026",
				   "startDate":%d,"endDate":%d},
				  {"gameCode":"lol","topLeagueId":"lck","leagueId":"lck_2026_event",
				   "startDate":%d,"endDate":%d},
				  {"gameCode":"lol","topLeagueId":"lck","leagueId":"lck_2027",
				   "startDate":%d,"endDate":%d},
				  {"gameCode":"lol","topLeagueId":"lec","leagueId":"lec_2026_summer",
				   "startDate":%d,"endDate":%d}
				]
				""".formatted(
				ms("2026-03-31T00:00:00Z"), ms("2026-09-12T14:59:59Z"),
				ms("2026-08-03T00:00:00Z"), ms("2026-08-03T14:59:59Z"),
				ms("2027-01-15T00:00:00Z"), ms("2027-09-01T00:00:00Z"),
				ms("2026-06-01T00:00:00Z"), ms("2026-10-01T00:00:00Z")));
	}

	private static long ms(String iso) {
		return Instant.parse(iso).toEpochMilli();
	}

	@Test
	void 진행_중인_시즌을_고른다() throws Exception {
		assertThat(NaverStandingsClient.pickLeagueId(leagues(), "lck", ms("2026-07-01T00:00:00Z")))
				.contains("lck_2026");
	}

	@Test
	void 이벤트_당일에도_기간이_긴_정규_시즌을_고른다() throws Exception {
		assertThat(NaverStandingsClient.pickLeagueId(leagues(), "lck", ms("2026-08-03T05:00:00Z")))
				.contains("lck_2026");
	}

	@Test
	void 오프시즌이면_가장_최근에_끝난_시즌을_고른다() throws Exception {
		assertThat(NaverStandingsClient.pickLeagueId(leagues(), "lck", ms("2026-09-24T00:00:00Z")))
				.contains("lck_2026");
	}

	@Test
	void 끝난_시즌이_없으면_비운다() throws Exception {
		assertThat(NaverStandingsClient.pickLeagueId(leagues(), "lck", ms("2026-01-01T00:00:00Z")))
				.isEmpty();
	}
}
