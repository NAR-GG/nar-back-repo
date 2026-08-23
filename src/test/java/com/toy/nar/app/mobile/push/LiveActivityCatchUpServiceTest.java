package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.live.ActiveLiveGame;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
	private LiveGameMinuteSnapshotRepository minuteSnapshotRepository;
	private LeagueMatchRepository leagueMatchRepository;
	private LiveActivityPushService pushService;
	private LiveActivityCatchUpService catchUpService;

	@BeforeEach
	void setUp() {
		liveStateStore = mock(LiveStateStore.class);
		minuteSnapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		pushService = mock(LiveActivityPushService.class);
		// 테스트에서는 즉시 실행 — 프로덕션은 applicationTaskExecutor 로 비동기.
		catchUpService = new LiveActivityCatchUpService(
				liveStateStore, minuteSnapshotRepository, leagueMatchRepository, pushService,
				Runnable::run);
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

	// ── 파드 분리 회귀 (#442) ─────────────────────────────
	// 구독 API 는 웹 파드가 처리하고 그 파드의 LiveStateStore 는 영구히 비어 있다.
	// 인메모리만 보면 따라잡기가 항상 no-op 이었다.

	@Test
	void 웹_파드처럼_인메모리가_비어도_신선한_프레임이_있으면_띄운다() {
		when(liveStateStore.getActiveGames()).thenReturn(Map.of());
		freshFrames();
		match();

		catchUpService.catchUpMatch(7L, MATCH_ID);

		// 인메모리 게임이 없으니 세트 번호는 스코어 합 + 1 로 추정한다(1:0 → 2세트).
		verify(pushService).startCardForMember(eq(MATCH_ID), eq(7L), eq(2), eq(1), eq(0), any());
	}

	@Test
	void 팀_구독도_인메모리가_비어도_DB_후보로_띄운다() {
		when(liveStateStore.getActiveGames()).thenReturn(Map.of());
		when(minuteSnapshotRepository.findFreshMatchIds(any())).thenReturn(List.of(MATCH_ID));
		freshFrames();
		match();

		catchUpService.catchUpTeam(7L, "blu");

		verify(pushService).startCardForMember(eq(MATCH_ID), eq(7L), eq(2), eq(1), eq(0), any());
	}

	@Test
	void DB_경로에서도_종료_확정된_세트는_띄우지_않는다() {
		when(liveStateStore.getActiveGames()).thenReturn(Map.of());
		freshFrames();
		when(liveStateStore.isFinished(GAME_ID)).thenReturn(true);

		catchUpService.catchUpMatch(7L, MATCH_ID);

		verifyNoStart();
	}

	@Test
	void 신선도_기준을_UTC_로_묻는다() {
		when(liveStateStore.getActiveGames()).thenReturn(Map.of());

		catchUpService.catchUpMatch(7L, MATCH_ID);

		ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(minuteSnapshotRepository).findFreshGameIdsByMatchId(eq(MATCH_ID), since.capture());
		// KST 로 물으면 UTC 기준 9시간 미래가 되어 아무 프레임도 안 잡힌다.
		LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
		assertThat(since.getValue()).isBefore(nowUtc);
		assertThat(since.getValue()).isAfter(nowUtc.minusMinutes(4));
	}

	private void freshFrames() {
		when(minuteSnapshotRepository.findFreshGameIdsByMatchId(eq(MATCH_ID), any()))
				.thenReturn(List.of(GAME_ID));
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
