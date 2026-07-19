package com.toy.nar.app.lolesports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 라이브 스코어 보정: 네이버가 Riot 보다 앞설 때만 채택. */
class LeagueMatchServicePickAheadScoreTest {

	@Test
	void 네이버가_앞서면_네이버_채택() {
		assertThat(LeagueMatchService.pickAheadScore(new int[] { 1, 0 }, 0, 0)).containsExactly(1, 0);
		assertThat(LeagueMatchService.pickAheadScore(new int[] { 2, 1 }, 1, 1)).containsExactly(2, 1);
	}

	@Test
	void 합이_같으면_Riot_유지() {
		// 네이버 0-1, Riot 1-0 — 합 동일. 채택 안 함(승자 방향이 달라도 앞선 게 아니면 건드리지 않음).
		assertThat(LeagueMatchService.pickAheadScore(new int[] { 0, 1 }, 1, 0)).isNull();
	}

	@Test
	void Riot이_앞서면_Riot_유지() {
		assertThat(LeagueMatchService.pickAheadScore(new int[] { 1, 0 }, 1, 1)).isNull();
	}

	@Test
	void 네이버_null이면_Riot_유지() {
		assertThat(LeagueMatchService.pickAheadScore(null, 0, 0)).isNull();
	}
}
