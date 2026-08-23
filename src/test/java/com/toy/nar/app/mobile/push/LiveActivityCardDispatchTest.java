package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 잠금화면 카드는 회원·매치당 한 장이다.
 *
 * <p>이전에는 세트가 시작될 때마다 카드를 새로 만들었다. 중복 방지가
 * {@code NOT EXISTS(active LiveActivityToken)} 과 30초 인메모리 창뿐이었는데, 갱신 토큰은 앱이
 * 실행돼야 올라오고 인메모리 창은 #442 로 파드가 둘이 된 뒤 세트 시작(스케줄러)과 구독 직후
 * 따라잡기(웹)가 서로 다른 JVM 이라 공유되지 않았다. 실측 2026-08-23 T1 vs HLE: 세트1 3,120건
 * 뒤 세트2 에 2,702건이 또 나가 약 2,700명이 카드 두 장을 받았고, 갱신 토큰이 없는 옛 카드는
 * 만들어진 시점 스코어(0:0)에 영구히 멈춰 있었다.</p>
 *
 * <p>선점 이력은 목이 아니라 상태를 들고 있는 대역으로 검증한다 — 호출마다 반환값을 정해 주면
 * "서비스가 선점을 실제로 존중하는지" 가 아니라 "스텁이 시킨 대로 하는지" 를 보게 된다.</p>
 */
class LiveActivityCardDispatchTest {

	private static final String MATCH_ID = "match-1";

	private LiveActivityTokenRepository tokenRepository;
	private LiveActivityStartTokenRepository startTokenRepository;
	private FakeLiveActivityCardDispatchRepository cardDispatchRepository;
	private ApnsLiveActivityClient apnsClient;
	private LiveActivityPushService service;

	@BeforeEach
	void setUp() {
		tokenRepository = mock(LiveActivityTokenRepository.class);
		startTokenRepository = mock(LiveActivityStartTokenRepository.class);
		cardDispatchRepository = new FakeLiveActivityCardDispatchRepository();
		apnsClient = mock(ApnsLiveActivityClient.class);
		service = new LiveActivityPushService(
				tokenRepository, startTokenRepository, cardDispatchRepository, apnsClient);
		when(apnsClient.isAvailable()).thenReturn(true);
		ReflectionTestUtils.setField(service, "pushToStartEnabled", true);
		when(apnsClient.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString()))
				.thenReturn(CompletableFuture.completedFuture(true));
		when(apnsClient.sendEndAsync(anyString(), any(), any()))
				.thenReturn(CompletableFuture.completedFuture(true));
	}

	@Test
	@DisplayName("세트가 바뀌어도 카드를 다시 만들지 않는다 — 매치당 한 장")
	void doesNotDispatchAgainOnNextSet() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		service.startCards(MATCH_ID, 2, 1, 0, 10L, 20L, attributes());

		verify(apnsClient, times(1))
				.sendStartAsync(eq("tok-a"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("선점은 회원 단위, 발송은 토큰 단위 — 기기가 여럿이면 전부 보낸다")
	void claimsPerMemberButSendsPerToken() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("iphone", 7L), startTarget("ipad", 7L)));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());

		verify(apnsClient).sendStartAsync(eq("iphone"), anyString(), any(), any(), anyString(), anyString());
		verify(apnsClient).sendStartAsync(eq("ipad"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("이미 받은 회원만 빠진다 — 같은 발송의 새 회원은 영향받지 않는다")
	void filtersOnlyAlreadyDispatchedMembers() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-7", 7L)))
				.thenReturn(List.of(startTarget("tok-7", 7L), startTarget("tok-8", 8L)));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		service.startCards(MATCH_ID, 2, 1, 0, 10L, 20L, attributes());

		verify(apnsClient, times(1))
				.sendStartAsync(eq("tok-7"), anyString(), any(), any(), anyString(), anyString());
		verify(apnsClient, times(1))
				.sendStartAsync(eq("tok-8"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("카드를 지운 뒤 재구독하면 다시 뜬다 — 따라잡기가 오래된 발행 이력을 푼다")
	void catchUpAfterWindowRecreatesCard() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));
		when(startTokenRepository.findStartTargetsForMember(MATCH_ID, 7L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		cardDispatchRepository.advance(Duration.ofMinutes(5));
		service.startCardForMember(MATCH_ID, 7L, 2, 1, 0, attributes());

		verify(apnsClient, times(2))
				.sendStartAsync(eq("tok-a"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("방금 발행한 카드는 따라잡기가 풀지 않는다 — 연속 구독 액션이 카드를 두 장 만들지 않는다")
	void catchUpWithinWindowDoesNotDuplicate() {
		// 실측 2026-08-16 member 10: 17:07:37 / 17:07:39 두 번 따라잡혀 2장이 됐다. 갱신 토큰이
		// 아직 안 올라온 창이라 closePreviousCard 가 닫을 토큰을 못 찾았다.
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));
		when(startTokenRepository.findStartTargetsForMember(MATCH_ID, 7L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		service.startCardForMember(MATCH_ID, 7L, 1, 0, 0, attributes());

		verify(apnsClient, times(1))
				.sendStartAsync(eq("tok-a"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("매치 종료 발송은 발행 이력을 정리한다 — 테이블이 무한히 자라지 않는다")
	void matchEndCleansUpDispatchRows() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));
		when(tokenRepository.findActivePushTokensByMatchId(MATCH_ID)).thenReturn(List.of("tok-a"));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		assertThat(cardDispatchRepository.hasDispatch(MATCH_ID, 7L)).isTrue();

		service.forceMatchEnd(MATCH_ID, 3, 2, 1, "HLE");

		assertThat(cardDispatchRepository.hasDispatch(MATCH_ID, 7L)).isFalse();
	}

	@Test
	@DisplayName("세트 종료(매치 진행 중)는 발행 이력을 지우지 않는다 — 지우면 다음 세트에 카드가 또 생긴다")
	void setEndKeepsDispatchRows() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));
		when(tokenRepository.findActivePushTokensByMatchId(MATCH_ID)).thenReturn(List.of("tok-a"));

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());
		service.notifySetEnd(MATCH_ID, 1, 1, 0, false, null);

		assertThat(cardDispatchRepository.hasDispatch(MATCH_ID, 7L)).isTrue();
	}

	@Test
	@DisplayName("선점 조회가 실패하면 선점 없이 전부 보낸다 — 카드 한 장 더보다 아무것도 못 받는 게 나쁘다")
	void sendsEverythingWhenClaimQueryFails() {
		when(startTokenRepository.findStartTargets(MATCH_ID, 10L, 20L))
				.thenReturn(List.of(startTarget("tok-a", 7L)));
		cardDispatchRepository.failClaims();

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());

		verify(apnsClient, times(1))
				.sendStartAsync(eq("tok-a"), anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("push-to-start 가 꺼져 있으면 선점도 하지 않는다")
	void doesNotClaimWhenPushToStartDisabled() {
		ReflectionTestUtils.setField(service, "pushToStartEnabled", false);

		service.startCards(MATCH_ID, 1, 0, 0, 10L, 20L, attributes());

		assertThat(cardDispatchRepository.hasDispatch(MATCH_ID, 7L)).isFalse();
		verify(apnsClient, never())
				.sendStartAsync(anyString(), anyString(), any(), any(), anyString(), anyString());
	}

	private LiveActivityStartTokenRepository.StartTargetRow startTarget(String pushToken, Long memberId) {
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
				return "HLE";
			}
		};
	}

	private LiveActivityPushService.MatchCardAttributes attributes() {
		return new LiveActivityPushService.MatchCardAttributes(
				MATCH_ID, "T1", "T1", "Hanwha Life Esports", "HLE", "LCK");
	}
}
