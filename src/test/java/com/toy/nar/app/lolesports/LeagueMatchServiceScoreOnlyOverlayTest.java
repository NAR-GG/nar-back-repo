package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 종료 확정을 보류할 때 이미 받아온 스코어를 스코어만이라도 선반영하는 판정.
 *
 * <p>세트 사이 KESPA 는 업스트림 state 가 unstarted 로 방치돼 디스커버리가 realtime sync
 * (네이버 오버레이 포함)에 도달하지 못한다. 실측 2026-08-10 DNS vs GEN: 세트1 종료(21:33) 후
 * 20분간 DB 0:0 고착, 다음 세트 픽밴의 Riot gameWins flip(21:53)에야 1:0. 그 사이 매 사이클
 * 돌던 네이버 종료 확정 호출은 스코어를 받아놓고 미완이라는 이유로 통째로 버리고 있었다.</p>
 */
class LeagueMatchServiceScoreOnlyOverlayTest {

	@Test
	void 네이버_스코어가_DB보다_앞서면_그_스코어를_돌려준다() {
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("inProgress", 0, 0), new int[] { 1, 0 }))
				.containsExactly(1, 0);
	}

	@Test
	void 같거나_뒤지면_null() {
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("inProgress", 1, 0), new int[] { 1, 0 })).isNull();
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("inProgress", 1, 1), new int[] { 1, 0 })).isNull();
	}

	@Test
	void 완료된_경기는_건드리지_않는다() {
		// 완료 스코어는 Riot 최종값이 진실이다. 네이버 wrong-high 가 완료 결과를 덮으면 안 된다.
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("completed", 2, 0), new int[] { 2, 1 })).isNull();
	}

	@Test
	void 업스트림이_방치한_unstarted_라도_스코어는_선반영한다() {
		// 이 판정이 필요한 바로 그 구간 — 세트 사이 KESPA 는 state 가 unstarted 로 온다.
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("unstarted", 0, 0), new int[] { 1, 0 }))
				.containsExactly(1, 0);
	}

	@Test
	void null_안전() {
		assertThat(LeagueMatchService.scoreOnlyOverlay(null, new int[] { 1, 0 })).isNull();
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("inProgress", 0, 0), null)).isNull();
		assertThat(LeagueMatchService.scoreOnlyOverlay(match("inProgress", null, null), new int[] { 1, 0 }))
				.containsExactly(1, 0);
	}

	@Test
	void applyScore_는_state_를_건드리지_않는다() {
		// 스코어 선반영이 state 를 옮기면 #354/#355 가 막은 되돌림 클래스가 재발한다.
		LeagueMatch match = match("inProgress", 0, 0);

		match.applyScore(1, 0, LocalDateTime.of(2026, 8, 10, 12, 40));

		assertThat(match.getState()).isEqualTo("inProgress");
		assertThat(match.getBlueScore()).isEqualTo(1);
		assertThat(match.getRedScore()).isEqualTo(0);
		assertThat(match.getLastUpdated()).isEqualTo(LocalDateTime.of(2026, 8, 10, 12, 40));
	}

	private LeagueMatch match(String state, Integer blueScore, Integer redScore) {
		return LeagueMatch.builder()
				.id("116929376557102192")
				.leagueName("KESPA")
				.state(state)
				.blueTeamCode("DNS")
				.blueScore(blueScore)
				.redTeamCode("GEN")
				.redScore(redScore)
				.bestOf(3)
				.build();
	}
}
