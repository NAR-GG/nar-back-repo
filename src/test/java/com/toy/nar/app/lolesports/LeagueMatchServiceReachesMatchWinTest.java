package com.toy.nar.app.lolesports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 RESULT 조기확정 가드. KESPA 는 네이버가 세트를 개별 경기로 올려 세트 사이에도
 * RESULT 가 오므로(2026-08-10 GEN vs HLE 실사고: bo3 이 세트1 직후 1:0 completed 고착),
 * 다전제 승리 조건에 도달한 스코어만 매치 종료로 인정한다.
 */
class LeagueMatchServiceReachesMatchWinTest {

	@Test
	void bo3_는_2승부터_종료다() {
		assertThat(LeagueMatchService.reachesMatchWin(3, 1, 0)).isFalse(); // 실사고 케이스
		assertThat(LeagueMatchService.reachesMatchWin(3, 1, 1)).isFalse();
		assertThat(LeagueMatchService.reachesMatchWin(3, 2, 0)).isTrue();
		assertThat(LeagueMatchService.reachesMatchWin(3, 1, 2)).isTrue();
	}

	@Test
	void bo1_은_1승이면_종료다() {
		assertThat(LeagueMatchService.reachesMatchWin(1, 1, 0)).isTrue();
		assertThat(LeagueMatchService.reachesMatchWin(1, 0, 1)).isTrue();
	}

	@Test
	void bo5_는_3승부터_종료다() {
		assertThat(LeagueMatchService.reachesMatchWin(5, 2, 2)).isFalse();
		assertThat(LeagueMatchService.reachesMatchWin(5, 3, 1)).isTrue();
	}

	@Test
	void bestOf_미상이면_종료로_단정하지_않는다() {
		// 틀린 조기확정은 추적 중단까지 번지므로, 미상이면 업스트림 flip 을 기다린다.
		assertThat(LeagueMatchService.reachesMatchWin(null, 2, 0)).isFalse();
		assertThat(LeagueMatchService.reachesMatchWin(0, 2, 0)).isFalse();
	}
}
