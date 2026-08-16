package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveActivityPushServiceTest {

	private LiveActivityTokenRepository tokenRepository;
	private LiveActivityStartTokenRepository startTokenRepository;
	private ApnsLiveActivityClient apnsClient;
	private LiveActivityPushService service;

	@BeforeEach
	void setUp() {
		tokenRepository = mock(LiveActivityTokenRepository.class);
		startTokenRepository = mock(LiveActivityStartTokenRepository.class);
		apnsClient = mock(ApnsLiveActivityClient.class);
		service = new LiveActivityPushService(tokenRepository, startTokenRepository, apnsClient);
		when(apnsClient.isAvailable()).thenReturn(true);
		when(apnsClient.sendUpdateAsync(anyString(), any())).thenReturn(alive(true));
		when(apnsClient.sendEndAsync(anyString(), any(), any())).thenReturn(alive(true));
	}

	@Test
	void 세트_시작은_진행중_상태와_스코어만_보낸다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 2, 1, 0);

		Map<String, Object> state = captureUpdate();
		assertThat(state).containsEntry("phase", "playing")
				.containsEntry("setNumber", 2)
				.containsEntry("scoreA", 1)
				.containsEntry("scoreB", 0)
				.doesNotContainKey("winnerTeamCode")
				// 카드에서 경과 시간을 뺐다. 시간 관련 필드는 싣지 않는다.
				.doesNotContainKey("setStartedAt")
				.doesNotContainKey("frozenTime");
	}

	@Test
	void 스코어가_null_이면_0으로_보낸다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 1, null, null);

		assertThat(captureUpdate()).containsEntry("scoreA", 0).containsEntry("scoreB", 0);
	}

	@Test
	void 세트만_끝나면_update_로_보내고_토큰을_유지한다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetEnd("match-1", 1, 1, 0, false, null);

		Map<String, Object> state = captureUpdate();
		assertThat(state).containsEntry("phase", "setEnded")
				.containsEntry("statusLabel", "다음 세트 준비 중")
				.doesNotContainKey("winnerTeamCode");
		verify(tokenRepository, never()).deactivateByPushTokenIn(any());
	}

	@Test
	void 매치가_끝나면_end_로_보내고_토큰을_정리한다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1", "tok-2"));

		service.notifySetEnd("match-1", 3, 2, 1, true, "T1");

		ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
		verify(apnsClient).sendEndAsync(eq("tok-1"), captor.capture(), any(Duration.class));
		assertThat(captor.getValue()).containsEntry("phase", "matchEnded")
				.containsEntry("statusLabel", "경기 종료")
				.containsEntry("winnerTeamCode", "T1");
		// 끝난 카드는 더 갱신되지 않으므로 매치 단위로 한 번에 정리한다(IN 절로 토큰을 나열하지 않는다).
		verify(tokenRepository).deactivateAllByMatchId("match-1");
		verify(tokenRepository, never()).deactivateByPushTokenIn(any());
	}

	@Test
	void APNs_가_죽었다고_한_토큰만_비활성화한다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("live", "dead"));
		when(apnsClient.sendUpdateAsync(eq("dead"), any())).thenReturn(alive(false));

		service.notifySetStart("match-1", 1, 0, 0);

		verify(tokenRepository).deactivateByPushTokenIn(List.of("dead"));
	}

	@Test
	void APNs_설정이_없으면_조회조차_하지_않는다() {
		when(apnsClient.isAvailable()).thenReturn(false);

		service.notifySetStart("match-1", 1, 0, 0);

		verify(tokenRepository, never()).findActivePushTokensByMatchId(anyString());
	}

	@Test
	void 카드가_없는_매치는_발송하지_않는다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of());

		service.notifySetStart("match-1", 1, 0, 0);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 카드가_많아도_왕복을_직렬로_쌓지_않는다() throws Exception {
		// 동기 발송을 토큰 수만큼 반복하면 카드가 많은 경기에서 마지막 사람은 세트가 끝난 뒤에
		// 카드가 갱신된다(FCM 쪽 1,500명 팬아웃 8~18분 실사고와 같은 모양).
		// 전부 띄운 뒤에 기다려야 하므로, 첫 응답이 늦어도 나머지 발송이 먼저 나가 있어야 한다.
		int cardCount = 200;
		List<String> tokens = java.util.stream.IntStream.range(0, cardCount)
				.mapToObj(i -> "tok-" + i).toList();
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(tokens);

		java.util.concurrent.CountDownLatch allDispatched =
				new java.util.concurrent.CountDownLatch(cardCount);
		java.util.concurrent.CompletableFuture<Boolean> blocked = new java.util.concurrent.CompletableFuture<>();
		when(apnsClient.sendUpdateAsync(anyString(), any())).thenAnswer(invocation -> {
			allDispatched.countDown();
			// 첫 토큰만 늦게 끝난다. 직렬이라면 여기서 막혀 나머지가 발송되지 않는다.
			return "tok-0".equals(invocation.getArgument(0)) ? blocked : alive(true);
		});

		Thread caller = new Thread(() -> service.notifySetStart("match-1", 1, 0, 0));
		// 직렬로 회귀하면 caller 가 blocked 에 갇힌다. 데몬으로 두어 실패가 빌드를 멈추지 않게 한다.
		caller.setDaemon(true);
		caller.start();

		boolean dispatchedWithoutWaiting;
		try {
			dispatchedWithoutWaiting = allDispatched.await(5, java.util.concurrent.TimeUnit.SECONDS);
		} finally {
			blocked.complete(true);
		}

		assertThat(dispatchedWithoutWaiting)
				.as("느린 토큰 하나가 나머지 발송을 막으면 안 된다")
				.isTrue();
		caller.join(5_000);
		verify(apnsClient, org.mockito.Mockito.times(cardCount)).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 다음_세트가_시작된_뒤_도착한_이전_세트_종료는_버린다() {
		// 2026-07-31 LCK Gen.G vs T1 실측: 업스트림이 끝난 게임 id 를 계속 실어 보내
		// 1세트 종료가 2세트 진행 중에 5회 추가 발화했다. 그대로 두면 2세트를 하는 내내
		// 카드가 "SET 1 종료 / 다음 세트 준비 중" 으로 덮인다.
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 1, 0, 0);
		service.notifySetEnd("match-1", 1, 0, 1, false, null);
		service.notifySetStart("match-1", 2, 0, 1);
		org.mockito.Mockito.clearInvocations(apnsClient);

		service.notifySetEnd("match-1", 1, 0, 1, false, null);   // 재발화
		service.notifySetEnd("match-1", 1, 0, 1, false, null);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 낡은_세트_종료가_매치_종료로_판정돼도_카드를_닫지_않는다() {
		// 치명 케이스: 재발화한 1세트 종료를 그 시점 스코어(0:2)로 계산하면 matchEnded=true 가 된다.
		// 그대로 나가면 end 이벤트 + 토큰 전체 비활성화라 남은 세트 동안 카드가 영구히 멈춘다.
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 1, 0, 0);
		service.notifySetEnd("match-1", 1, 0, 1, false, null);
		service.notifySetStart("match-1", 2, 0, 1);
		org.mockito.Mockito.clearInvocations(apnsClient, tokenRepository);

		service.notifySetEnd("match-1", 1, 0, 2, true, "GEN");   // 낡은 세트 번호 + 현재 스코어

		verify(apnsClient, never()).sendEndAsync(anyString(), any(), any());
		verify(tokenRepository, never()).deactivateAllByMatchId(anyString());
	}

	@Test
	void 정상_순서는_모두_통과한다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 1, 0, 0);
		service.notifySetEnd("match-1", 1, 1, 0, false, null);
		service.notifySetStart("match-1", 2, 1, 0);
		service.notifySetEnd("match-1", 2, 1, 1, false, null);
		service.notifySetStart("match-1", 3, 1, 1);

		verify(apnsClient, org.mockito.Mockito.times(5)).sendUpdateAsync(anyString(), any());

		service.notifySetEnd("match-1", 3, 2, 1, true, "T1");
		verify(apnsClient).sendEndAsync(anyString(), any(), any());
	}

	@Test
	void forceMatchEnd_는_워터마크가_더_높아도_발송한다() {
		// 리메이크로 폴링 워터마크(3세트 playing=30)가 스코어 합 기반 세트(2)보다 높은 상황.
		// notifySetEnd 라면 키 22 < 30 으로 기각되지만, 스윕의 근거는 DB 확정 상태라 우회한다.
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));
		service.notifySetStart("match-1", 3, 1, 0);

		service.forceMatchEnd("match-1", 2, 2, 0, "T1");

		verify(apnsClient).sendEndAsync(anyString(), any(), any());
		assertThat(service.matchEndPushed("match-1")).isTrue();
	}

	@Test
	void 매치_종료가_나가면_늦은_setEnded_역행을_가드가_본다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		assertThat(service.matchEndPushed("match-1")).isFalse();
		assertThat(service.claimMatchEndPush("match-1")).isTrue();
		// 두 번째 선점은 실패 — 발송 경로 셋(편승·복구·스윕)의 dedup.
		assertThat(service.claimMatchEndPush("match-1")).isFalse();
		assertThat(service.matchEndPushed("match-1")).isTrue();
	}

	@Test
	void notifySetEnd_매치종료도_발송_기록을_남긴다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetEnd("match-1", 2, 2, 0, true, "T1");

		assertThat(service.matchEndPushed("match-1")).isTrue();
	}

	@Test
	void 같은_상태_재발화는_통과시킨다() {
		// 같은 세트를 다시 그리는 것뿐이라 무해하고, 그 사이 스코어가 갱신됐을 수 있다.
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 2, 1, 0);
		service.notifySetStart("match-1", 2, 1, 0);

		verify(apnsClient, org.mockito.Mockito.times(2)).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 세트_번호를_모르면_카드를_갱신하지_않는다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 0, 0, 0);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 매치가_달라지면_진행도가_섞이지_않는다() {
		when(tokenRepository.findActivePushTokensByMatchId(anyString())).thenReturn(List.of("tok-1"));

		service.notifySetStart("match-1", 3, 1, 1);
		org.mockito.Mockito.clearInvocations(apnsClient);

		// 다른 경기의 1세트는 match-1 의 3세트와 무관하게 통과해야 한다.
		service.notifySetStart("match-2", 1, 0, 0);

		verify(apnsClient).sendUpdateAsync(anyString(), any());
	}

	@Test
	void 토큰_조회가_실패해도_예외가_새지_않는다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1"))
				.thenThrow(new RuntimeException("DB down"));

		service.notifySetStart("match-1", 1, 0, 0);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}

	// ── push-to-start ──────────────────────────────

	@Test
	void 구독자에게_카드를_새로_만든다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L))
				.thenReturn(List.of(startTarget("start-tok", 1L, "T1")));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		ArgumentCaptor<Map<String, Object>> attrs = mapCaptor();
		ArgumentCaptor<Map<String, Object>> state = mapCaptor();
		verify(apnsClient).sendStartAsync(eq("start-tok"), eq("MatchLiveAttributes"),
				attrs.capture(), state.capture(),
				// alert 없이 보내면 iOS 가 start 를 버린다 — 문구까지 계약이다.
				eq("T1 vs Hanwha Life Esports"), eq("1세트 시작"));
		assertThat(attrs.getValue())
				.containsEntry("matchId", "match-1")
				.containsEntry("teamACode", "T1")
				.containsEntry("teamBCode", "HLE")
				.containsEntry("leagueName", "LCK")
				// 앱과 합의한 캐시 파일명 규칙
				.containsEntry("teamALogoFile", "T1.png")
				.containsEntry("teamBLogoFile", "HLE.png")
				.containsEntry("favoriteTeamCode", "T1");
		assertThat(state.getValue()).containsEntry("phase", "playing").containsEntry("setNumber", 1);
	}

	@Test
	void 응원팀이_없으면_하트_필드를_빼고_보낸다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L))
				.thenReturn(List.of(startTarget("start-tok", 1L, null)));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		ArgumentCaptor<Map<String, Object>> attrs = mapCaptor();
		verify(apnsClient).sendStartAsync(anyString(), anyString(), attrs.capture(), any(),
				anyString(), anyString());
		assertThat(attrs.getValue()).doesNotContainKey("favoriteTeamCode");
	}

	@Test
	void 회원마다_응원팀이_달라_payload_를_따로_만든다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L)).thenReturn(List.of(
				startTarget("tok-a", 1L, "T1"),
				startTarget("tok-b", 2L, "HLE")));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		ArgumentCaptor<Map<String, Object>> attrs = mapCaptor();
		verify(apnsClient, org.mockito.Mockito.times(2))
				.sendStartAsync(anyString(), anyString(), attrs.capture(), any(),
						anyString(), anyString());
		assertThat(attrs.getAllValues()).extracting(m -> m.get("favoriteTeamCode"))
				.containsExactly("T1", "HLE");
	}

	@Test
	void push_to_start_스위치가_꺼져_있으면_조회조차_하지_않는다() {
		// APNs 자체는 켜져 있어도 카드 생성만 따로 끌 수 있어야 한다.
		when(apnsClient.isAvailable()).thenReturn(true);

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		verify(startTokenRepository, never()).findStartTargets(anyString(), any(), any());
	}

	@Test
	void 대상이_없으면_발송하지_않는다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L)).thenReturn(List.of());

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		verify(apnsClient, never()).sendStartAsync(anyString(), anyString(), any(), any(),
				anyString(), anyString());
	}

	@Test
	void 죽은_push_to_start_토큰만_비활성화한다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L)).thenReturn(List.of(
				startTarget("live", 1L, null),
				startTarget("dead", 2L, null)));
		when(apnsClient.sendStartAsync(eq("dead"), anyString(), any(), any(), anyString(), anyString()))
				.thenReturn(alive(false));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		verify(startTokenRepository).deactivateByPushTokenIn(List.of("dead"));
	}

	@Test
	void 대상_조회가_실패해도_예외가_새지_않는다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargets(anyString(), any(), any()))
				.thenThrow(new RuntimeException("DB down"));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		verify(apnsClient, never()).sendStartAsync(anyString(), anyString(), any(), any(),
				anyString(), anyString());
	}

	/**
	 * 중복 카드의 실제 원인. 카드가 뜬 뒤 앱이 갱신 토큰을 올리기까지 실측 2~10초 걸리는데,
	 * 그 전에 두 번째 요청이 들어오면 {@code NOT EXISTS(active LiveActivityToken)} 가드가
	 * 통과해 카드가 두 장 뜬다 — 2026-08-16 매치 115548147900619033 에서 member 10 이
	 * 17:07:37 / 17:07:39 두 번 catch-up 되고 "이전 카드 닫음" 로그 없이 2장이 됐다.
	 */
	@Test
	void 방금_카드를_보낸_회원에게는_다시_보내지_않는다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargetsForMember("match-1", 1L))
				.thenReturn(List.of(startTarget("start-tok", 1L, "T1")));

		service.startCardForMember("match-1", 1L, 1, 0, 0, attributes());
		service.startCardForMember("match-1", 1L, 1, 0, 0, attributes());

		verify(apnsClient, org.mockito.Mockito.times(1))
				.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	void 다른_회원의_카드는_막지_않는다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargetsForMember("match-1", 1L))
				.thenReturn(List.of(startTarget("tok-a", 1L, "T1")));
		when(startTokenRepository.findStartTargetsForMember("match-1", 2L))
				.thenReturn(List.of(startTarget("tok-b", 2L, "HLE")));

		service.startCardForMember("match-1", 1L, 1, 0, 0, attributes());
		service.startCardForMember("match-1", 2L, 1, 0, 0, attributes());

		verify(apnsClient, org.mockito.Mockito.times(2))
				.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	void 다른_경기의_카드는_막지_않는다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargetsForMember(anyString(), eq(1L)))
				.thenReturn(List.of(startTarget("start-tok", 1L, "T1")));

		service.startCardForMember("match-1", 1L, 1, 0, 0, attributes());
		service.startCardForMember("match-2", 1L, 1, 0, 0, attributes());

		verify(apnsClient, org.mockito.Mockito.times(2))
				.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString());
	}

	/** 세트 시작 일괄 발송도 같은 창을 쓴다 — catch-up 직후 세트가 시작돼도 두 장이 되면 안 된다. */
	@Test
	void catch_up_직후_세트시작_일괄발송은_그_회원을_건너뛴다() {
		enablePushToStart();
		when(startTokenRepository.findStartTargetsForMember("match-1", 1L))
				.thenReturn(List.of(startTarget("start-tok", 1L, "T1")));
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L)).thenReturn(List.of(
				startTarget("start-tok", 1L, "T1"),
				startTarget("tok-b", 2L, "HLE")));

		service.startCardForMember("match-1", 1L, 1, 0, 0, attributes());
		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		// member 1 은 방금 받았으므로 건너뛰고, member 2 만 새로 받는다.
		verify(apnsClient, org.mockito.Mockito.times(1))
				.sendStartAsync(eq("start-tok"), anyString(), any(), any(), anyString(), anyString());
		verify(apnsClient, org.mockito.Mockito.times(1))
				.sendStartAsync(eq("tok-b"), anyString(), any(), any(), anyString(), anyString());
	}

	private void enablePushToStart() {
		when(apnsClient.isAvailable()).thenReturn(true);
		org.springframework.test.util.ReflectionTestUtils.setField(service, "pushToStartEnabled", true);
		when(apnsClient.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString()))
				.thenReturn(alive(true));
	}

	private LiveActivityPushService.MatchCardAttributes attributes() {
		return new LiveActivityPushService.MatchCardAttributes(
				"match-1", "T1", "T1", "Hanwha Life Esports", "HLE", "LCK");
	}

	private LiveActivityStartTokenRepository.StartTargetRow startTarget(
			String pushToken, Long memberId, String favoriteTeamCode) {
		return new LiveActivityStartTokenRepository.StartTargetRow() {
			@Override
			public String getPushToken() {
				return pushToken;
			}

			@Override
			public Long getMemberId() {
				return memberId;
			}

			@Override
			public String getFavoriteTeamCode() {
				return favoriteTeamCode;
			}
		};
	}

	private Map<String, Object> captureUpdate() {
		ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
		verify(apnsClient).sendUpdateAsync(anyString(), captor.capture());
		return captor.getValue();
	}

	private java.util.concurrent.CompletableFuture<Boolean> alive(boolean value) {
		return java.util.concurrent.CompletableFuture.completedFuture(value);
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Map<String, Object>> mapCaptor() {
		return ArgumentCaptor.forClass(Map.class);
	}
}
