package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.live.ActiveLiveGame;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveActivityCatchUpServiceTest {

	private static final String MATCH_ID = "match-1";
	private static final String GAME_ID = "game-1";

	private LiveStateStore liveStateStore;
	private LeagueMatchRepository leagueMatchRepository;
	private LiveActivityPushService pushService;
	private LiveActivityCatchUpService catchUpService;

	@BeforeEach
	void setUp() {
		liveStateStore = mock(LiveStateStore.class);
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		pushService = mock(LiveActivityPushService.class);
		// 테스트에서는 즉시 실행 — 프로덕션은 applicationTaskExecutor 로 비동기.
		catchUpService = new LiveActivityCatchUpService(
				liveStateStore, leagueMatchRepository, pushService, Runnable::run);
		when(pushService.isEnabled()).thenReturn(true);
	}

	@Test
	void 진행_중인_경기를_구독하면_그_회원에게_카드를_띄운다() {
		liveGame(2);
		match();

		catchUpService.catchUpMatch(7L, MATCH_ID);

		verify(pushService).startCardForMember(eq(MATCH_ID), eq(7L), eq(2), eq(1), eq(0), any());
	}

	@Test
	void 세트_번호가_없으면_스코어_합_다음_세트로_추정한다() {
		liveGame(null);
		match();

		catchUpService.catchUpMatch(7L, MATCH_ID);

		// 1:0 이면 지금 진행 중인 세트는 2세트다.
		verify(pushService).startCardForMember(eq(MATCH_ID), eq(7L), eq(2), eq(1), eq(0), any());
	}

	@Test
	void 진행_중인_세트가_없으면_띄우지_않는다() {
		when(liveStateStore.getActiveGames()).thenReturn(Map.of());

		catchUpService.catchUpMatch(7L, MATCH_ID);

		verifyNoStart();
	}

	@Test
	void 종료_확정된_세트의_잔상으로는_띄우지_않는다() {
		liveGame(2);
		when(liveStateStore.isFinished(GAME_ID)).thenReturn(true);

		catchUpService.catchUpMatch(7L, MATCH_ID);

		verifyNoStart();
	}

	@Test
	void 팀_구독은_그_팀이_뛰는_진행_중_경기만_띄운다() {
		liveGame(2);
		match();

		catchUpService.catchUpTeam(7L, "blu");

		verify(pushService).startCardForMember(eq(MATCH_ID), eq(7L), eq(2), eq(1), eq(0), any());
	}

	@Test
	void 팀_구독에서_그_팀이_없는_경기는_건너뛴다() {
		liveGame(2);
		match();

		catchUpService.catchUpTeam(7L, "T1");

		verifyNoStart();
	}

	@Test
	void APNs_가_꺼져_있으면_아무것도_하지_않는다() {
		when(pushService.isEnabled()).thenReturn(false);

		catchUpService.catchUpMatch(7L, MATCH_ID);

		verifyNoStart();
	}

	private void liveGame(Integer setNumber) {
		ActiveLiveGame game = new ActiveLiveGame(
				GAME_ID, MATCH_ID, "LCK", "Blue", "Red", LocalDateTime.now(), 0,
				setNumber, null, null);
		when(liveStateStore.getActiveGames()).thenReturn(Map.of(GAME_ID, game));
	}

	private void match() {
		LeagueMatch match = LeagueMatch.builder()
				.id(MATCH_ID)
				.leagueName("LCK")
				.state("inProgress")
				.blueTeamCode("BLU")
				.blueTeamName("Blue")
				.redTeamCode("RED")
				.redTeamName("Red")
				.blueScore(1)
				.redScore(0)
				.bestOf(3)
				.build();
		when(leagueMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
	}

	private void verifyNoStart() {
		verify(pushService, never())
				.startCardForMember(anyString(), anyLong(), anyInt(), any(), any(), any());
	}
}
