package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 블록명 라벨은 프로덕션 {@code league_match.match_title} 에서 실제로 관찰한 값들이다
 * (2026-08-22, LCK·LPL·LEC·LCS·CBLOL·KeSPA·LCP).
 */
class StandingsBlocksTest {

	@DisplayName("주차 블록은 정규다")
	@ParameterizedTest
	@ValueSource(strings = {
			"1주 차 | BRO vs HLE",
			"9주 차 | NS vs DK",
			"13주 차 | DK vs GEN",   // LCK Split 3
			"5주 차 | BLG vs TES"})  // LPL
	void weekBlocksAreRegular(String title) {
		assertThat(StandingsBlocks.isRegular(title)).isTrue();
	}

	@DisplayName("주차 표기를 안 쓰는 정규 스테이지도 통과한다")
	@ParameterizedTest
	@ValueSource(strings = {
			"그룹 | GEN vs T1",      // KeSPA 조별리그
			"스위스 | CFO vs GAM"})  // LCP
	void nonWeekRegularBlocksPass(String title) {
		assertThat(StandingsBlocks.isRegular(title)).isTrue();
	}

	@DisplayName("플레이오프 계열은 순위 집계에서 빠진다")
	@ParameterizedTest
	@ValueSource(strings = {
			"플레이-인 | TBD vs TBD",
			"플레이오프 | TBD vs TBD",
			"결승 | TBD vs TBD",
			"토너먼트 스테이지 | HLE vs T1",
			"플레이-인 토너먼트 스테이지 | TBD vs TBD",
			"대표 선발전 | TBD vs TBD"})  // LPL
	void playoffBlocksAreExcluded(String title) {
		assertThat(StandingsBlocks.isRegular(title)).isFalse();
	}

	@DisplayName("모르는 라벨은 통과시키지 않는다 — 화이트리스트라 조용히 섞이지 않는다")
	@Test
	void unknownBlockIsExcluded() {
		assertThat(StandingsBlocks.isRegular("승격강등전 | A vs B")).isFalse();
		assertThat(StandingsBlocks.isRegular(null)).isFalse();
		assertThat(StandingsBlocks.isRegular("")).isFalse();
	}

	@DisplayName("구분자가 없으면 제목 전체를 블록명으로 본다")
	@Test
	void titleWithoutSeparator() {
		assertThat(StandingsBlocks.blockNameOf("그룹")).isEqualTo("그룹");
		assertThat(StandingsBlocks.blockNameOf("13주 차 | DK vs GEN")).isEqualTo("13주 차");
	}
}
