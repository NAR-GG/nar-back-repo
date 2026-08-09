package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
	void 끝난_매치의_살아있는_카드에_매치_종료를_보낸다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "completed", 2, 0)));

		sweeper.sweep();

		// 세트 번호 = 스코어 합, 승자 = 스코어 우세 팀 코드.
		verify(pushService).notifySetEnd("match-1", 2, 2, 0, true, "BLU");
	}

	@Test
	void 진행_중인_매치는_건드리지_않는다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "inProgress", 1, 0)));

		sweeper.sweep();

		verify(pushService, never()).notifySetEnd(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyBoolean(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void 스코어_미상이면_승자_없이_세트1로_닫는다() {
		when(tokenRepository.findDistinctActiveMatchIds()).thenReturn(List.of("match-1"));
		when(leagueMatchRepository.findAllById(List.of("match-1")))
				.thenReturn(List.of(match("match-1", "completed", null, null)));

		sweeper.sweep();

		verify(pushService).notifySetEnd("match-1", 1, 0, 0, true, null);
	}

	@Test
	void APNs_비활성이면_조회조차_하지_않는다() {
		when(pushService.isEnabled()).thenReturn(false);

		sweeper.sweep();

		verifyNoInteractions(tokenRepository, leagueMatchRepository);
	}

	private static LeagueMatch match(String id, String state, Integer blue, Integer red) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName("LCK")
				.state(state)
				.blueTeamCode("BLU")
				.blueScore(blue)
				.redTeamCode("RED")
				.redScore(red)
				.build();
	}
}
