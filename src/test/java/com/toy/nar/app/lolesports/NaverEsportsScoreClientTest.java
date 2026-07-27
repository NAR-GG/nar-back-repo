package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverEsportsScoreClientTest {

	/** 2026-07-18 22:30 KST */
	private static final long START_MS = 1784381400000L;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private JsonNode day(String matchesJson) throws Exception {
		return objectMapper.readTree("{\"code\":200,\"content\":{\"matches\":[" + matchesJson + "]}}");
	}

	private static String lolMatch(String home, String away, int homeScore, int awayScore, long startDate) {
		return String.format("""
				{"gameCode":"lol","matchStatus":"STARTED","homeScore":%d,"awayScore":%d,"startDate":%d,
				 "homeTeam":{"nameEngAcronym":"%s"},"awayTeam":{"nameEngAcronym":"%s"}}
				""", homeScore, awayScore, startDate, home, away);
	}

	private static final String GEN_DK = lolMatch("GEN", "DK", 1, 2, START_MS);

	@Test
	void 블루_레드가_홈_어웨이와_같은_방향이면_그대로_매핑한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "GEN", "DK", START_MS);

		assertThat(score).containsExactly(1, 2);
	}

	@Test
	void 블루_레드가_뒤집혀_있으면_스왑해서_매핑한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "DK", "GEN", START_MS);

		assertThat(score).containsExactly(2, 1);
	}

	@Test
	void 대소문자_무시하고_매칭한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "gen", "dk", START_MS);

		assertThat(score).containsExactly(1, 2);
	}

	@Test
	void LoL이_아닌_종목은_같은_약칭이어도_무시한다() throws Exception {
		// 같은 조직이 타 종목(발로란트 등)에서 같은 날 붙는 경우
		JsonNode root = day("""
				{"gameCode":"val","matchStatus":"RESULT","homeScore":2,"awayScore":0,"startDate":%d,
				 "homeTeam":{"nameEngAcronym":"GEN"},"awayTeam":{"nameEngAcronym":"DK"}}
				""".formatted(START_MS));

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK", START_MS)).isNull();
	}

	@Test
	void 같은_팀_쌍이_두_경기면_시작_시각이_가까운_경기를_고른다() throws Exception {
		// 더블헤더: 낮 경기(끝남, 3:1) + 밤 경기(진행 중, 0:1). 밤 경기 기준으로 조회.
		long dayGameStart = START_MS - 8 * 3600_000L;
		JsonNode root = day(lolMatch("GEN", "DK", 3, 1, dayGameStart) + "," + lolMatch("GEN", "DK", 0, 1, START_MS));

		int[] score = NaverEsportsScoreClient.extractScore(root, "GEN", "DK", START_MS);

		assertThat(score).containsExactly(0, 1);
	}

	@Test
	void 시작_시각이_6시간_이상_어긋나면_매칭하지_않는다() throws Exception {
		JsonNode root = day(lolMatch("GEN", "DK", 3, 1, START_MS - 7 * 3600_000L));

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK", START_MS)).isNull();
	}

	@Test
	void 시작_전_경기는_무시한다() throws Exception {
		JsonNode root = day("""
				{"gameCode":"lol","matchStatus":"BEFORE","homeScore":0,"awayScore":0,"startDate":%d,
				 "homeTeam":{"nameEngAcronym":"GEN"},"awayTeam":{"nameEngAcronym":"DK"}}
				""".formatted(START_MS));

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK", START_MS)).isNull();
	}

	@Test
	void 팀_정보가_없는_경기는_건너뛴다() throws Exception {
		JsonNode root = day("""
				{"gameCode":"lol","matchStatus":"STARTED","homeScore":0,"awayScore":0,"startDate":%d,
				 "homeTeam":null,"awayTeam":null},
				""".formatted(START_MS) + GEN_DK);

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK", START_MS)).containsExactly(1, 2);
	}

	@Test
	void 매칭되는_경기가_없으면_null() throws Exception {
		assertThat(NaverEsportsScoreClient.extractScore(day(GEN_DK), "T1", "KC", START_MS)).isNull();
	}

	@Test
	void matchStatus가_RESULT면_종료로_판정한다() throws Exception {
		JsonNode root = day("""
				{"gameCode":"lol","matchStatus":"RESULT","homeScore":2,"awayScore":0,"startDate":%d,
				 "homeTeam":{"nameEngAcronym":"GEN"},"awayTeam":{"nameEngAcronym":"DK"}}
				""".formatted(START_MS));

		NaverEsportsScoreClient.Result result =
				NaverEsportsScoreClient.extractResult(root, "GEN", "DK", START_MS);

		assertThat(result.finished()).isTrue();
		assertThat(result.score()).containsExactly(2, 0);
	}

	@Test
	void matchStatus가_STARTED면_종료로_판정하지_않는다() throws Exception {
		NaverEsportsScoreClient.Result result =
				NaverEsportsScoreClient.extractResult(day(GEN_DK), "GEN", "DK", START_MS);

		assertThat(result.finished()).isFalse();
		assertThat(result.score()).containsExactly(1, 2);
	}

	@Test
	void 응답이_null이면_null() {
		assertThat(NaverEsportsScoreClient.extractScore(null, "GEN", "DK", START_MS)).isNull();
	}
}
