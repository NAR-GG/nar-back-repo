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
		when(apnsClient.sendUpdate(anyString(), any())).thenReturn(true);
		when(apnsClient.sendEnd(anyString(), any(), any())).thenReturn(true);
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
		verify(apnsClient).sendEnd(eq("tok-1"), captor.capture(), any(Duration.class));
		assertThat(captor.getValue()).containsEntry("phase", "matchEnded")
				.containsEntry("statusLabel", "경기 종료")
				.containsEntry("winnerTeamCode", "T1");
		// 끝난 카드는 더 갱신되지 않으므로 죽은 토큰 여부와 무관하게 전부 비활성화한다.
		verify(tokenRepository).deactivateByPushTokenIn(List.of("tok-1", "tok-2"));
	}

	@Test
	void APNs_가_죽었다고_한_토큰만_비활성화한다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1")).thenReturn(List.of("live", "dead"));
		when(apnsClient.sendUpdate(eq("dead"), any())).thenReturn(false);

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

		verify(apnsClient, never()).sendUpdate(anyString(), any());
	}

	@Test
	void 토큰_조회가_실패해도_예외가_새지_않는다() {
		when(tokenRepository.findActivePushTokensByMatchId("match-1"))
				.thenThrow(new RuntimeException("DB down"));

		service.notifySetStart("match-1", 1, 0, 0);

		verify(apnsClient, never()).sendUpdate(anyString(), any());
	}

	private Map<String, Object> captureUpdate() {
		ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
		verify(apnsClient).sendUpdate(anyString(), captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Map<String, Object>> mapCaptor() {
		return ArgumentCaptor.forClass(Map.class);
	}
}
