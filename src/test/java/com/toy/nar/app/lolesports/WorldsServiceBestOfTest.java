package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * match.strategy.count 파싱 검증.
 * 실측 응답 형태 — getSchedule 은 {"type":"bestOf","count":3}, getEventDetails 는 {"count":3} 을 준다.
 */
class WorldsServiceBestOfTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("strategy.count 를 그대로 읽는다")
	void readsStrategyCount() {
		ObjectNode match = objectMapper.createObjectNode();
		match.putObject("strategy").put("type", "bestOf").put("count", 5);

		assertThat(WorldsService.parseBestOf(match)).isEqualTo(5);
	}

	@Test
	@DisplayName("strategy 가 없으면 null — 리그명으로 추정하지 않는다")
	void missingStrategyIsNull() {
		assertThat(WorldsService.parseBestOf(objectMapper.createObjectNode())).isNull();
	}

	@Test
	@DisplayName("count 가 0 이면 null 로 본다")
	void zeroCountIsNull() {
		ObjectNode match = objectMapper.createObjectNode();
		match.putObject("strategy").put("count", 0);

		assertThat(WorldsService.parseBestOf(match)).isNull();
	}
}
