package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업스트림이 끝난 경기를 결과 없는 상태로 되돌려 보낼 때 기존 결과를 지키는지.
 *
 * <p>2026-08-04 KESPA T1 vs HLE 가 18:02 에 네이버로 2:1 completed 확정된 뒤, 30분 주기
 * 전체 동기화가 unstarted 0:0 으로 덮어써 결과가 사라졌다. 디스커버리의 네이버 재확정은
 * matchId 당 1회라 되돌려진 뒤에는 스스로 복구되지 않았다.</p>
 */
class LeagueMatchServiceResultRegressionTest {

	@Test
	void 완료된_경기를_미시작_0대0_으로_되돌리면_막는다() {
		LeagueMatch existing = match("completed", 2, 1);
		LeagueMatch incoming = match("unstarted", 0, 0);

		assertThat(LeagueMatchService.isResultRegression(existing, incoming)).isTrue();
	}

	@Test
	void 완료된_경기를_진행중_0대0_으로_되돌려도_막는다() {
		// 방치 상태가 unstarted 로만 오는 것은 아니다. 결과가 사라지는 것은 같다.
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", 2, 0), match("inProgress", 0, 0))).isTrue();
	}

	@Test
	void 스코어가_실제로_바뀌는_갱신은_통과시킨다() {
		// 리메이크·오기 정정은 0 이 아닌 스코어로 들어온다.
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", 2, 1), match("completed", 2, 0))).isFalse();
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", 2, 1), match("completed", 1, 2))).isFalse();
	}

	@Test
	void 아직_결과가_없는_경기는_대상이_아니다() {
		// 시작 전 경기에 0:0 이 오는 것은 정상이다.
		assertThat(LeagueMatchService.isResultRegression(
				match("unstarted", 0, 0), match("unstarted", 0, 0))).isFalse();
		assertThat(LeagueMatchService.isResultRegression(
				match("inProgress", 1, 0), match("inProgress", 0, 0))).isFalse();
	}

	@Test
	void 완료됐지만_스코어가_0대0_이면_대상이_아니다() {
		// 지킬 결과가 없으므로 업스트림 값을 그대로 받는다.
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", 0, 0), match("unstarted", 0, 0))).isFalse();
	}

	@Test
	void 스코어가_null_이어도_터지지_않는다() {
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", null, null), match("unstarted", null, null))).isFalse();
		assertThat(LeagueMatchService.isResultRegression(
				match("completed", 2, null), match("unstarted", null, null))).isTrue();
	}

	@Test
	void 되돌림을_막을_때_기존_상태와_스코어를_그대로_옮긴다() {
		LeagueMatch incoming = match("unstarted", 0, 0);

		incoming.restoreResult("completed", 2, 1);

		assertThat(incoming.getState()).isEqualTo("completed");
		assertThat(incoming.getBlueScore()).isEqualTo(2);
		assertThat(incoming.getRedScore()).isEqualTo(1);
	}

	private LeagueMatch match(String state, Integer blueScore, Integer redScore) {
		return LeagueMatch.builder()
				.id("116929376557102172")
				.leagueName("KESPA")
				.state(state)
				.blueTeamCode("T1")
				.blueScore(blueScore)
				.redTeamCode("HLE")
				.redScore(redScore)
				.bestOf(3)
				.build();
	}
}
