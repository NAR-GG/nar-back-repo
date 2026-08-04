package com.toy.nar.app.mobile.push;

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
	private ApnsLiveActivityClient apnsClient;
	private LiveActivityPushService service;

	@BeforeEach
	void setUp() {
		tokenRepository = mock(LiveActivityTokenRepository.class);
		apnsClient = mock(ApnsLiveActivityClient.class);
		service = new LiveActivityPushService(tokenRepository, apnsClient);
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
