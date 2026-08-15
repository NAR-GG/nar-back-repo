package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 구독 액션으로 카드를 다시 띄울 때는 그 회원의 이전 카드를 먼저 닫는다.
 *
 * <p>사용자가 잠금화면에서 카드를 지워도 서버는 그걸 모른다(앱에 해제 API 호출 경로가 없다).
 * 갱신 토큰이 active 로 남아 {@code findStartTargetsForMember} 의 중복 방지 조건에 걸려,
 * 구독을 취소하고 다시 신청해도 카드가 영원히 안 떴다 — 실측 2026-08-12 DK vs KT:
 * 20:03 카드 생성 뒤 삭제, 20:52 구독 해제·재신청에도 push-to-start 발송 0건.</p>
 *
 * <p>이전 카드를 닫고(이미 없으면 무해) 토큰을 비활성화한 뒤 새로 발행하면, 지운 경우는
 * 다시 뜨고 살아있는 경우도 카드가 두 장 되지 않는다.</p>
 */
class LiveActivityPushServiceRecreateTest {

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
		org.springframework.test.util.ReflectionTestUtils.setField(service, "pushToStartEnabled", true);
		when(apnsClient.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString()))
				.thenReturn(CompletableFuture.completedFuture(true));
		when(apnsClient.sendEndAsync(anyString(), any(), any()))
				.thenReturn(CompletableFuture.completedFuture(true));
	}

	@Test
	@DisplayName("이전 카드를 즉시 닫고 토큰을 비활성화한 뒤 새 카드를 발행한다")
	void closesPreviousCardThenStartsNew() {
		when(tokenRepository.findActivePushTokensByMemberIdAndMatchId(7L, "match-1"))
				.thenReturn(List.of("old-tok"));
		when(startTokenRepository.findStartTargetsForMember("match-1", 7L))
				.thenReturn(List.of(startTarget("start-tok", 7L, "HLE")));

		service.startCardForMember("match-1", 7L, 2, 1, 0, attributes());

		// 닫기 → 토큰 정리 → 발송 순서. 정리가 발송보다 늦으면 중복 방지 조건이 새 카드를 막는다.
		InOrder order = inOrder(apnsClient, tokenRepository, startTokenRepository);
		order.verify(apnsClient).sendEndAsync(eq("old-tok"), any(), eq(Duration.ZERO));
		order.verify(tokenRepository).deactivateByPushTokenIn(List.of("old-tok"));
		order.verify(startTokenRepository).findStartTargetsForMember("match-1", 7L);
		order.verify(apnsClient).sendStartAsync(eq("start-tok"), anyString(), any(), any(),
				anyString(), anyString());
	}

	@Test
	@DisplayName("이전 카드가 없으면 닫기 없이 바로 발행한다")
	void startsDirectlyWhenNoPreviousCard() {
		when(tokenRepository.findActivePushTokensByMemberIdAndMatchId(7L, "match-1"))
				.thenReturn(List.of());
		when(startTokenRepository.findStartTargetsForMember("match-1", 7L))
				.thenReturn(List.of(startTarget("start-tok", 7L, "HLE")));

		service.startCardForMember("match-1", 7L, 1, 0, 0, attributes());

		verify(apnsClient, never()).sendEndAsync(anyString(), any(), any());
		verify(tokenRepository, never()).deactivateByPushTokenIn(any());
		verify(apnsClient).sendStartAsync(eq("start-tok"), anyString(), any(), any(),
				anyString(), anyString());
	}

	@Test
	@DisplayName("닫기 푸시가 실패해도 새 카드 발행은 진행한다")
	void startsEvenWhenCloseFails() {
		when(tokenRepository.findActivePushTokensByMemberIdAndMatchId(7L, "match-1"))
				.thenReturn(List.of("old-tok"));
		when(apnsClient.sendEndAsync(anyString(), any(), any()))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("apns down")));
		when(startTokenRepository.findStartTargetsForMember("match-1", 7L))
				.thenReturn(List.of(startTarget("start-tok", 7L, "HLE")));

		service.startCardForMember("match-1", 7L, 2, 1, 0, attributes());

		// 유령 토큰이 남아 재생성을 막는 편이 카드 두 장보다 나쁘다 — 정리는 발송 성공과 무관하게 한다.
		verify(tokenRepository).deactivateByPushTokenIn(List.of("old-tok"));
		verify(apnsClient).sendStartAsync(eq("start-tok"), anyString(), any(), any(),
				anyString(), anyString());
	}

	@Test
	@DisplayName("닫기 상태는 진행 중 스코어를 그대로 실어 카드가 잘못된 값으로 남지 않게 한다")
	void closePayloadCarriesCurrentScore() {
		when(tokenRepository.findActivePushTokensByMemberIdAndMatchId(7L, "match-1"))
				.thenReturn(List.of("old-tok"));
		when(startTokenRepository.findStartTargetsForMember("match-1", 7L))
				.thenReturn(List.of(startTarget("start-tok", 7L, "HLE")));

		service.startCardForMember("match-1", 7L, 2, 1, 0, attributes());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> state = ArgumentCaptor.forClass(Map.class);
		verify(apnsClient).sendEndAsync(eq("old-tok"), state.capture(), any());
		assertThat(state.getValue()).containsEntry("scoreA", 1).containsEntry("scoreB", 0);
	}

	@Test
	@DisplayName("전체 대상 발행(startCards)은 이전 카드를 닫지 않는다 — 세트 시작 경로는 그대로")
	void startCardsDoesNotCloseAnything() {
		when(startTokenRepository.findStartTargets("match-1", 10L, 20L))
				.thenReturn(List.of(startTarget("start-tok", 7L, "HLE")));

		service.startCards("match-1", 1, 0, 0, 10L, 20L, attributes());

		verify(apnsClient, never()).sendEndAsync(anyString(), any(), any());
		verify(tokenRepository, never()).findActivePushTokensByMemberIdAndMatchId(any(), anyString());
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
}
