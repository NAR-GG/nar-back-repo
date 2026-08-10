package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveActivityOrphanCardSweeperTest {

	private LiveActivityTokenRepository tokenRepository;
	private LeagueMatchRepository leagueMatchRepository;
	private LiveActivityPushService pushService;
	private LiveActivityOrphanCardSweeper sweeper;

	@BeforeEach
	void setUp() {
		tokenRepository = mock(LiveActivityTokenRepository.class);
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		pushService = mock(LiveActivityPushService.class);
		// 테스트에서는 즉시 실행 — 프로덕션은 applicationTaskExecutor 로 비동기.
		sweeper = new LiveActivityOrphanCardSweeper(
				tokenRepository, leagueMatchRepository, pushService, Runnable::run);
		when(pushService.isEnabled()).thenReturn(true);
	}

	@Test
	void 끝난_매치의_살아있는_카드에_매치_종료를_강제_발송한다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "completed", 3, 2, 0)));

		sweeper.sweep();

		// forceMatchEnd: DB 확정 상태 기반이라 워터마크 검사를 우회한다. 세트 = 스코어 합.
		verify(pushService).forceMatchEnd("match-1", 2, 2, 0, "BLU");
	}

	@Test
	void 진행_중인_매치는_건드리지_않는다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "inProgress", 3, 1, 0)));

		sweeper.sweep();

		verifyNoForceMatchEnd();
	}

	@Test
	void completed_라도_승리_조건_미달이면_오염_의심으로_보류한다() {
		// 2026-08-10 GEN vs HLE 실사고: 네이버 조기 RESULT 로 bo3 이 1:0 completed.
		// 이걸 믿고 쓸면 진행 중 경기 카드를 닫고 토큰을 죽여 복구 불가가 된다.
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "completed", 3, 1, 0)));

		sweeper.sweep();

		verifyNoForceMatchEnd();
	}

	@Test
	void bestOf_미상_completed_는_검증_불가라_보류한다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "completed", null, 2, 0)));

		sweeper.sweep();

		verifyNoForceMatchEnd();
	}

	@Test
	void APNs_비활성이면_조회조차_하지_않는다() {
		when(pushService.isEnabled()).thenReturn(false);

		sweeper.sweep();

		verifyNoInteractions(tokenRepository, leagueMatchRepository);
	}

	private void verifyNoForceMatchEnd() {
		verify(pushService, never()).forceMatchEnd(anyString(), anyInt(), any(), any(), any());
	}

	private static LeagueMatch match(String id, String state, Integer bestOf, Integer blue, Integer red) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName("LCK")
				.state(state)
				.bestOf(bestOf)
				.blueTeamCode("BLU")
				.blueScore(blue)
				.redTeamCode("RED")
				.redScore(red)
				.build();
	}
}
