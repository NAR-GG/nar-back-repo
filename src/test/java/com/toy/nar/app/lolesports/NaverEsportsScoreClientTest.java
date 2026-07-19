package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverEsportsScoreClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private JsonNode day(String matchesJson) throws Exception {
		return objectMapper.readTree("{\"code\":200,\"content\":{\"matches\":[" + matchesJson + "]}}");
	}

	private static final String GEN_DK = """
			{"gameCode":"lol","matchStatus":"STARTED","homeScore":1,"awayScore":2,
			 "homeTeam":{"nameEngAcronym":"GEN"},"awayTeam":{"nameEngAcronym":"DK"}}
			""";

	@Test
	void 블루_레드가_홈_어웨이와_같은_방향이면_그대로_매핑한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "GEN", "DK");

		assertThat(score).containsExactly(1, 2);
	}

	@Test
	void 블루_레드가_뒤집혀_있으면_스왑해서_매핑한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "DK", "GEN");

		assertThat(score).containsExactly(2, 1);
	}

	@Test
	void 대소문자_무시하고_매칭한다() throws Exception {
		int[] score = NaverEsportsScoreClient.extractScore(day(GEN_DK), "gen", "dk");

		assertThat(score).containsExactly(1, 2);
	}

	@Test
	void 시작_전_경기는_무시한다() throws Exception {
		JsonNode root = day("""
				{"gameCode":"lol","matchStatus":"BEFORE","homeScore":0,"awayScore":0,
				 "homeTeam":{"nameEngAcronym":"GEN"},"awayTeam":{"nameEngAcronym":"DK"}}
				""");

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK")).isNull();
	}

	@Test
	void 팀_정보가_없는_경기는_건너뛴다() throws Exception {
		JsonNode root = day("""
				{"gameCode":"game","matchStatus":"STARTED","homeScore":0,"awayScore":0,
				 "homeTeam":null,"awayTeam":null},
				""" + GEN_DK);

		assertThat(NaverEsportsScoreClient.extractScore(root, "GEN", "DK")).containsExactly(1, 2);
	}

	@Test
	void 매칭되는_경기가_없으면_null() throws Exception {
		assertThat(NaverEsportsScoreClient.extractScore(day(GEN_DK), "T1", "KC")).isNull();
	}

	@Test
	void 응답이_null이면_null() {
		assertThat(NaverEsportsScoreClient.extractScore(null, "GEN", "DK")).isNull();
	}
}
