package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LolesportsStageClientTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static JsonNode json(String s) throws Exception {
		return MAPPER.readTree(s);
	}

	@DisplayName("지정한 스테이지의 경기 id 만 뽑는다")
	@Test
	void picksOnlyNamedStage() throws Exception {
		JsonNode root = json("""
				{"data":{"standings":[{"stages":[
				 {"name":"Swiss","sections":[{"matches":[{"id":"1"},{"id":"2"}]}]},
				 {"name":"Round 4","sections":[{"matches":[{"id":"3"}]}]}]}]}}""");

		assertThat(LolesportsStageClient.parseStageMatchIds(root, "Swiss")).contains(Set.of("1", "2"));
	}

	@DisplayName("스테이지가 없으면 비어 있다")
	@Test
	void emptyWhenStageMissing() throws Exception {
		JsonNode root = json("{\"data\":{\"standings\":[{\"stages\":[{\"name\":\"Playoffs\",\"sections\":[]}]}]}}");

		assertThat(LolesportsStageClient.parseStageMatchIds(root, "Swiss")).isEmpty();
	}

	@DisplayName("가장 최근에 시작한 토너먼트를 고른다")
	@Test
	void picksLatestTournament() throws Exception {
		JsonNode root = json("""
				{"data":{"leagues":[{"tournaments":[
				 {"id":"old","startDate":"2025-10-01"},{"id":"new","startDate":"2026-10-02"}]}]}}""");

		assertThat(LolesportsStageClient.latestTournamentId(root)).isEqualTo("new");
	}
}
