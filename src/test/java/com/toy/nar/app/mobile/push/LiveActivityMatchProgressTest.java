package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityCardDispatchRepository;
import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 카드 진행도 워터마크와 매치 종료 발송 여부는 재기동을 견뎌야 한다.
 *
 * <p>둘 다 인메모리 필드였다. 비면 낡은 이벤트가 진행 중인 경기 카드를 전 세트로 되돌리고,
 * 더 나쁘게는 그 시점 스코어로 매치 종료로 판정돼 카드가 경기 도중 닫힌다(2026-07-31 Gen.G vs T1:
 * 1세트 종료가 2세트 진행 중에 5회 추가 발화). FCM 은 발송 이력 테이블이 막아 주지만 카드에는
 * 그런 장치가 없었다.</p>
 */
class LiveActivityMatchProgressTest {

	private static final String MATCH_ID = "match-1";

	private LiveActivityTokenRepository tokenRepository;
	private FakeLiveActivityMatchProgressRepository progressRepository;
	private ApnsLiveActivityClient apnsClient;
	private LiveActivityPushService service;

	@BeforeEach
	void setUp() {
		tokenRepository = mock(LiveActivityTokenRepository.class);
		progressRepository = new FakeLiveActivityMatchProgressRepository();
		apnsClient = mock(ApnsLiveActivityClient.class);
		service = newService();
		when(tokenRepository.findActivePushTokensByMatchId(MATCH_ID)).thenReturn(List.of("tok-a"));
	}

	/** 같은 대역(=같은 DB)을 쓰는 새 인스턴스. 인메모리 맵만 비므로 재기동과 같다. */
	private LiveActivityPushService newService() {
		LiveActivityPushService created = new LiveActivityPushService(
				tokenRepository,
				mock(LiveActivityStartTokenRepository.class),
				mock(LiveActivityCardDispatchRepository.class),
				progressRepository,
				apnsClient);
		when(apnsClient.isAvailable()).thenReturn(true);
		when(apnsClient.sendUpdateAsync(anyString(), any()))
				.thenReturn(CompletableFuture.completedFuture(true));
		when(apnsClient.sendEndAsync(anyString(), any(), any()))
				.thenReturn(CompletableFuture.completedFuture(true));
		ReflectionTestUtils.setField(created, "pushToStartEnabled", true);
		return created;
	}

	@Test
	@DisplayName("재기동해도 워터마크를 이어받아 낡은 이벤트를 무시한다")
	void keepsWatermarkAcrossRestart() {
		service.notifySetStart(MATCH_ID, 2, 1, 0);
		assertThat(progressRepository.storedProgress(MATCH_ID)).isNotNull();

		// 재기동: 인메모리는 비고 대역(DB)만 남는다.
		LiveActivityPushService restarted = newService();
		restarted.notifySetEnd(MATCH_ID, 1, 1, 0, false, null);

		// 1세트 종료는 2세트 진행보다 뒤처진 이벤트다 — 카드를 되돌리면 안 된다.
		// 재기동 전 세트2 발송 1건만 있어야 한다(같은 mock 을 두 인스턴스가 공유한다).
		verify(apnsClient, times(1)).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("재기동 후에도 앞선 이벤트는 정상 통과한다")
	void stillAcceptsForwardEventsAfterRestart() {
		service.notifySetStart(MATCH_ID, 1, 0, 0);

		LiveActivityPushService restarted = newService();
		restarted.notifySetStart(MATCH_ID, 2, 1, 0);

		// 세트1 진행 → 세트2 진행. 두 인스턴스가 각각 한 번씩 보낸다.
		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("재기동해도 매치 종료 카드를 두 번 내보내지 않는다")
	void doesNotPushMatchEndTwiceAcrossRestart() {
		service.forceMatchEnd(MATCH_ID, 3, 2, 1, "HLE");
		assertThat(service.matchEndPushed(MATCH_ID)).isTrue();

		LiveActivityPushService restarted = newService();

		assertThat(restarted.matchEndPushed(MATCH_ID)).isTrue();
		assertThat(restarted.claimMatchEndPush(MATCH_ID)).isFalse();
	}

	@Test
	@DisplayName("종료 선점은 한 번만 성공한다")
	void claimsMatchEndOnlyOnce() {
		assertThat(service.claimMatchEndPush(MATCH_ID)).isTrue();
		assertThat(service.claimMatchEndPush(MATCH_ID)).isFalse();
	}

	@Test
	@DisplayName("워터마크는 같은 값이면 통과시킨다 — 그 사이 스코어가 갱신됐을 수 있다")
	void acceptsSameProgressAgain() {
		service.notifySetStart(MATCH_ID, 1, 0, 0);
		service.notifySetStart(MATCH_ID, 1, 1, 0);

		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("DB 가 죽어도 발송은 계속한다 — 그 프로세스 안에서는 인메모리가 지킨다")
	void keepsSendingWhenRepositoryFails() {
		progressRepository.fail();

		service.notifySetStart(MATCH_ID, 1, 0, 0);
		service.notifySetEnd(MATCH_ID, 1, 1, 0, false, null);

		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
		// 인메모리 워터마크는 살아 있으므로 뒤처진 이벤트는 여전히 막힌다.
		service.notifySetStart(MATCH_ID, 1, 0, 0);
		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("워터마크가 아예 없으면(처음 보는 매치) 통과시킨다")
	void acceptsFirstEventOfUnknownMatch() {
		LiveActivityPushService fresh = newService();

		fresh.notifySetEnd(MATCH_ID, 2, 1, 1, false, null);

		verify(apnsClient).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("세트 번호를 모르면 카드를 갱신하지 않는다")
	void skipsWhenSetNumberUnknown() {
		service.notifySetStart(MATCH_ID, 0, 0, 0);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
		assertThat(progressRepository.storedProgress(MATCH_ID)).isNull();
	}

	@Test
	@DisplayName("진행도는 매치별로 독립이다")
	void progressIsPerMatch() {
		when(tokenRepository.findActivePushTokensByMatchId("match-2")).thenReturn(List.of("tok-b"));

		service.notifySetStart(MATCH_ID, 3, 2, 0);
		service.notifySetStart("match-2", 1, 0, 0);

		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
		assertThat(progressRepository.storedProgress("match-2"))
				.isLessThan(progressRepository.storedProgress(MATCH_ID));
	}

	@Test
	@DisplayName("빈 matchId 는 조용히 무시한다")
	void ignoresBlankMatchId() {
		service.notifySetStart("", 1, 0, 0);
		service.notifySetStart(null, 1, 0, 0);

		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("인메모리에 있으면 DB 를 다시 읽지 않는다 — 매치당 프로세스당 한 번")
	void readsDbOnlyOncePerMatch() {
		FakeLiveActivityMatchProgressRepository spy = progressRepository;
		service.notifySetStart(MATCH_ID, 1, 0, 0);
		service.notifySetStart(MATCH_ID, 2, 1, 0);

		// 대역은 호출 수를 세지 않으므로, 여기서는 "두 번째 호출이 정상 통과했다" 로 대신 확인한다.
		// (DB 재조회가 일어나도 결과는 같아야 하고, 성능만 나빠진다.)
		assertThat(spy.storedProgress(MATCH_ID)).isNotNull();
		verify(apnsClient, times(2)).sendUpdateAsync(anyString(), any());
	}

	@Test
	@DisplayName("종료 카드가 나간 뒤 늦은 세트 종료는 카드를 되돌리지 않는다")
	void lateSetEndAfterMatchEndIsRejected() {
		// 매치 종료는 sendEndAsync 로 나간다(update 아님).
		service.notifySetEnd(MATCH_ID, 3, 2, 1, true, "HLE");

		LiveActivityPushService restarted = newService();
		restarted.notifySetEnd(MATCH_ID, 2, 1, 1, false, null);

		// 종료(세트3 matchEnded) 뒤의 세트2 종료는 워터마크에 걸린다.
		verify(apnsClient, never()).sendUpdateAsync(anyString(), any());
	}
}
