package com.toy.nar.app.lolesports;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진행 중 경기의 스코어·상태 후퇴를 막는다.
 *
 * <p>{@link LeagueMatchService#isResultRegression}은 completed 결과만 지킨다. 그래서 아직 진행 중인
 * 경기는 30분 주기 전체 동기화가 업스트림 stale 값으로 조건 없이 덮어썼다 — 실측 2026-08-12
 * DNS vs GEN: 19:04 에 1:0 inProgress 였던 매치가 19:30 크론에 unstarted 0:0 으로 되돌아가고
 * 19:30:33 네이버 선반영이 스코어만 복구했다. 매시 :00/:30 마다 리스트·상세가 0:0 으로 깜빡이고,
 * state 는 세트 사이 unstarted 로 고착했다.</p>
 *
 * <p>업스트림 gameWins 는 다음 세트 픽밴에야 뒤집혀 몇 분간 뒤처지고(네이버 오버레이가 항상
 * 앞선다), 세트가 끝난 뒤 스코어가 줄어들 이유는 없다. 그래서 비완료 갱신은 스코어·상태를
 * 각각 앞선 값으로 유지한다. 최종 정정은 completed 갱신이 담당한다(self-heal).</p>
 */
class LeagueMatchServiceLiveRegressionTest {

	@Test
	@DisplayName("진행 중 1:0 을 미시작 0:0 으로 되돌리면 스코어·상태 모두 지킨다")
	void keepsScoreAndStateWhenCronRevertsToUnstarted() {
		LeagueMatch existing = match("inProgress", 1, 0);
		LeagueMatch incoming = match("unstarted", 0, 0);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("inProgress");
		assertThat(incoming.getBlueScore()).isEqualTo(1);
		assertThat(incoming.getRedScore()).isZero();
	}

	@Test
	@DisplayName("스코어만 후퇴하면 스코어만 지키고 상태는 수신값을 쓴다")
	void keepsOnlyScoreWhenStateIsNotBehind() {
		LeagueMatch existing = match("inProgress", 1, 1);
		LeagueMatch incoming = match("inProgress", 1, 0);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("inProgress");
		assertThat(incoming.getBlueScore()).isEqualTo(1);
		assertThat(incoming.getRedScore()).isEqualTo(1);
	}

	@Test
	@DisplayName("상태만 후퇴하면 상태만 지키고 전진한 스코어는 수신값을 쓴다")
	void keepsOnlyStateWhenScoreMovedForward() {
		LeagueMatch existing = match("inProgress", 1, 0);
		LeagueMatch incoming = match("unstarted", 1, 1);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("inProgress");
		assertThat(incoming.getBlueScore()).isEqualTo(1);
		assertThat(incoming.getRedScore()).isEqualTo(1);
	}

	@Test
	@DisplayName("전진하는 갱신은 그대로 통과시킨다")
	void passesForwardUpdate() {
		LeagueMatch existing = match("inProgress", 1, 0);
		LeagueMatch incoming = match("inProgress", 1, 1);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getBlueScore()).isEqualTo(1);
		assertThat(incoming.getRedScore()).isEqualTo(1);
	}

	@Test
	@DisplayName("수신이 completed 면 손대지 않는다 — 최종 스코어 정정과 self-heal 경로다")
	void doesNotTouchCompletedIncoming() {
		LeagueMatch existing = match("inProgress", 1, 1);
		LeagueMatch incoming = match("completed", 2, 1);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("completed");
		assertThat(incoming.getBlueScore()).isEqualTo(2);
		assertThat(incoming.getRedScore()).isEqualTo(1);
	}

	@Test
	@DisplayName("기존이 completed 면 대상이 아니다 — isResultRegression 이 담당한다")
	void doesNotTouchCompletedExisting() {
		LeagueMatch existing = match("completed", 2, 0);
		LeagueMatch incoming = match("inProgress", 1, 0);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("inProgress");
		assertThat(incoming.getBlueScore()).isEqualTo(1);
	}

	@Test
	@DisplayName("시작 전 경기에 0:0 이 오는 것은 정상이다")
	void unstartedZeroIsNotRegression() {
		LeagueMatch existing = match("unstarted", 0, 0);
		LeagueMatch incoming = match("unstarted", 0, 0);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("unstarted");
		assertThat(incoming.getBlueScore()).isZero();
	}

	@Test
	@DisplayName("스코어가 null 이어도 터지지 않는다")
	void nullScoresAreSafe() {
		LeagueMatch existing = match("inProgress", 1, 0);
		LeagueMatch incoming = match("unstarted", null, null);

		LeagueMatchService.preserveLiveProgress(existing, incoming);

		assertThat(incoming.getState()).isEqualTo("inProgress");
		assertThat(incoming.getBlueScore()).isEqualTo(1);
		assertThat(incoming.getRedScore()).isZero();
	}

	private LeagueMatch match(String state, Integer blueScore, Integer redScore) {
		return LeagueMatch.builder()
				.id("116929376557102192")
				.leagueName("LCK")
				.state(state)
				.blueTeamCode("DNS")
				.blueScore(blueScore)
				.redTeamCode("GEN")
				.redScore(redScore)
				.bestOf(3)
				.build();
	}
}
