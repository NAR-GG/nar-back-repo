package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스코어 전이 → 세트별 승자 귀속 검증.
 * 업스트림이 세트별 승자를 주지 않아(전수 실측) 스코어가 오르는 순간의 델타가 유일한 실시간 소스다.
 */
class LeagueMatchServiceSetWinnersTest {

	@Test
	@DisplayName("세트가 하나씩 끝나면 오른 팀에게 순서대로 귀속된다")
	void attributesEachSetInOrder() {
		String w = LeagueMatchService.advanceSetWinners(null, 1, 0, false);
		assertThat(w).isEqualTo("B");
		w = LeagueMatchService.advanceSetWinners(w, 1, 1, false);
		assertThat(w).isEqualTo("B,R");
		w = LeagueMatchService.advanceSetWinners(w, 2, 1, true);
		assertThat(w).isEqualTo("B,R,B");
	}

	@Test
	@DisplayName("스코어 변화가 없으면 그대로 둔다")
	void keepsWhenUnchanged() {
		assertThat(LeagueMatchService.advanceSetWinners("B,R", 1, 1, false)).isEqualTo("B,R");
		assertThat(LeagueMatchService.advanceSetWinners(null, 0, 0, false)).isNull();
	}

	@Test
	@DisplayName("동기화 공백 중 두 팀이 함께 오르면 순서 미상('?')으로 자리만 잡는다")
	void unknownOrderWhenBothIncreased() {
		assertThat(LeagueMatchService.advanceSetWinners(null, 1, 1, false)).isEqualTo("?,?");
		assertThat(LeagueMatchService.advanceSetWinners("B", 2, 1, false)).isEqualTo("B,?,?");
	}

	@Test
	@DisplayName("한쪽이 0이면 '?' 세트도 상대 승으로 소급 확정된다")
	void zeroSideResolvesUnknowns() {
		assertThat(LeagueMatchService.advanceSetWinners("?,?", 0, 3, false)).isEqualTo("R,R,R");
		assertThat(LeagueMatchService.advanceSetWinners(null, 2, 0, true)).isEqualTo("B,B");
	}

	@Test
	@DisplayName("진행 중 스코어 후퇴(stale 업스트림)는 기존 귀속을 지킨다")
	void staleRegressionKeepsCurrentWhileLive() {
		// DB 는 네이버 오버레이로 1-1 인데 30분 sync 가 Riot stale 1-0 을 들고 온 상황
		assertThat(LeagueMatchService.advanceSetWinners("B,R", 1, 0, false)).isEqualTo("B,R");
	}

	@Test
	@DisplayName("최종 스코어(completed)가 기존 귀속과 모순이면 재구축한다")
	void completedRegressionRebuilds() {
		// 네이버 wrong-high 로 잘못 적힌 상태를 최종 스코어가 정정
		assertThat(LeagueMatchService.advanceSetWinners("B,R,B", 2, 0, true)).isEqualTo("B,B");
		assertThat(LeagueMatchService.advanceSetWinners("B,B", 2, 1, true)).isEqualTo("B,B,R");
	}

	@Test
	@DisplayName("완료 재구축도 혼합 스코어면 순서를 지어내지 않는다")
	void completedRebuildStaysHonest() {
		// 귀속(B 2승)이 최종 1-2 와 모순 → 재구축하되 혼합 스코어라 순서는 미상
		assertThat(LeagueMatchService.advanceSetWinners("B,B,R", 1, 2, true)).isEqualTo("?,?,?");
	}
}
