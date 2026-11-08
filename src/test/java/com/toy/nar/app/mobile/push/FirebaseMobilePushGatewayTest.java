package com.toy.nar.app.mobile.push;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.toy.nar.app.data.source.NotificationService;
import com.google.firebase.messaging.MulticastMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소리 나는 발송 payload 를 지키는 회귀 테스트.
 *
 * <p>알림 잠자기는 무음 발송이 아니라 발송 자체를 건너뛰는 방식이라
 * ({@link QuietAwarePushSender}) 게이트웨이가 만드는 payload 는 한 종류뿐이다.
 * 여기가 조용히 바뀌면 <b>모든</b> 알림의 소리·배너가 사라지는데 예외도 로그도 안 남는다.</p>
 *
 * <p>firebase-admin(9.7.0 고정)의 AndroidConfig/ApnsConfig/Aps 는 Google HTTP client
 * {@code @Key} 직렬화 기반이라 public 게터가 없다. 소스 jar 로 확인한 실제 구조:</p>
 * <ul>
 *   <li>AndroidConfig 의 "priority" 필드는 {@code Priority} enum 이 아니라 그 이름을 소문자로
 *       바꾼 {@code String}("high"/"normal")이다 — 빌더가 생성 시점에 변환한다.</li>
 *   <li>ApnsConfig 는 "aps" 필드를 따로 갖지 않는다. 생성 시점에 Aps.getFields() 를
 *       "payload"라는 Map 필드에 key "aps" 로 즉시 합쳐 넣는다. Aps 자신도 sound 등
 *       개별 필드가 없고 전부 "fields" Map 에 담아 둔다.</li>
 * </ul>
 */
class FirebaseMobilePushGatewayTest {

	@SuppressWarnings("unchecked")
	private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider = mock(ObjectProvider.class);

	private final NotificationService notificationService = mock(NotificationService.class);

	private final FirebaseMobilePushGateway gateway =
			new FirebaseMobilePushGateway(firebaseMessagingProvider, notificationService);

	@Test
	@DisplayName("안드로이드는 HIGH 우선순위로 보내고 채널을 지정하지 않는다")
	void 안드로이드는_HIGH_우선순위로_보낸다() {
		AndroidConfig config = gateway.androidConfig();

		// HIGH 가 죽으면 기존 알림 전부의 헤드업(배너) 표시가 조용히 사라진다.
		assertThat(ReflectionTestUtils.getField(config, "priority")).isEqualTo("high");
		// 채널을 지정하지 않으므로 앱 매니페스트 기본 채널로 간다.
		assertThat(ReflectionTestUtils.getField(config, "notification")).isNull();
	}

	@Test
	@DisplayName("iOS 는 기본 사운드로 보낸다")
	@SuppressWarnings("unchecked")
	void iOS_는_기본_사운드로_보낸다() {
		ApnsConfig config = gateway.apnsConfig();

		Map<String, Object> payload = (Map<String, Object>) ReflectionTestUtils.getField(config, "payload");
		Map<String, Object> apsFields = (Map<String, Object>) payload.get("aps");
		assertThat(apsFields.get("sound")).isEqualTo("default");
		// 잠자기를 무음 발송으로 되돌리면 이 단정이 먼저 깨진다.
		assertThat(apsFields.get("interruption-level")).isNull();
	}

	@Test
	@DisplayName("오래 쉬었으면 첫 토큰 1건으로 연결부터 열고, 바로 이은 발송은 한 묶음으로 보낸다")
	@SuppressWarnings("unchecked")
	void 오래_쉬었으면_첫_토큰으로_연결부터_연다() throws Exception {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(mock(BatchResponse.class)));
		MobilePushMessage message = new MobilePushMessage("t", "b", Map.of());

		gateway.send(List.of("a", "b", "c"), message); // 첫 발송 = 쉰 시간 모름 → 선개통
		gateway.send(List.of("d", "e", "f"), message); // 방금 보냈다 → 그대로

		ArgumentCaptor<MulticastMessage> sent = ArgumentCaptor.forClass(MulticastMessage.class);
		verify(messaging, times(3)).sendEachForMulticastAsync(sent.capture());
		assertThat(sent.getAllValues())
				.extracting(m -> (List<String>) ReflectionTestUtils.getField(m, "tokens"))
				.containsExactly(List.of("a"), List.of("b", "c"), List.of("d", "e", "f"));
	}

	@Test
	@DisplayName("FCM 응답이 안 오면 상한에서 끊고 예외를 던진다 — 발송 스레드를 영영 잡아 두지 않는다")
	void 응답이_안_오면_상한에서_끊는다() {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		SettableApiFuture<BatchResponse> neverCompletes = SettableApiFuture.create();
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class))).thenReturn(neverCompletes);
		gateway.batchTimeout = Duration.ofMillis(50);

		// 2026-11-02: 이 대기가 무한이라 스케줄러 스레드가 22시간 멈췄다.
		assertThatThrownBy(() -> gateway.send(List.of("a"), new MobilePushMessage("t", "b", Map.of())))
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(TimeoutException.class);
		assertThat(neverCompletes.isCancelled()).isTrue();
	}

	private static BatchResponse allFailed(MessagingErrorCode code, int tokens) {
		FirebaseMessagingException cause = mock(FirebaseMessagingException.class);
		when(cause.getMessagingErrorCode()).thenReturn(code);
		SendResponse failed = mock(SendResponse.class);
		when(failed.isSuccessful()).thenReturn(false);
		when(failed.getException()).thenReturn(cause);
		BatchResponse response = mock(BatchResponse.class);
		when(response.getResponses()).thenReturn(java.util.Collections.nCopies(tokens, failed));
		when(response.getFailureCount()).thenReturn(tokens);
		return response;
	}

	@Test
	@DisplayName("토큰 탓이 아닌 전원 실패가 3번 연속이면 클라이언트를 갈아 끼우고 이후 발송은 새 클라이언트로 간다")
	void 연속_인프라_실패면_클라이언트를_갈아_끼운다() {
		FirebaseMessaging wedged = mock(FirebaseMessaging.class);
		FirebaseMessaging fresh = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(wedged);
		when(wedged.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 2)));
		when(fresh.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(mock(BatchResponse.class)));
		gateway.rebuilder = () -> fresh;
		MobilePushMessage message = new MobilePushMessage("t", "b", Map.of());

		// 2026-11-07: 풀이 꼬인 뒤 모든 발송이 수 ms 만에 전량 실패했는데 40시간 동안 아무도 몰랐다.
		gateway.send(List.of("a", "b"), message);
		gateway.send(List.of("a", "b"), message);
		verify(notificationService, never()).sendSchedulerWarningNotification(any(), any());
		gateway.send(List.of("a", "b"), message); // 3번째에서 교체

		verify(notificationService).sendSchedulerWarningNotification(any(), any());
		gateway.send(List.of("a", "b"), message);
		verify(wedged, times(3)).sendEachForMulticastAsync(any(MulticastMessage.class));
		verify(fresh, times(1)).sendEachForMulticastAsync(any(MulticastMessage.class));
	}

	@Test
	@DisplayName("죽은 토큰(UNREGISTERED)만 모여 실패한 건 장애로 세지 않는다")
	void 죽은_토큰_실패는_장애가_아니다() {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(MessagingErrorCode.UNREGISTERED, 2)));
		boolean[] rebuilt = {false};
		gateway.rebuilder = () -> {
			rebuilt[0] = true;
			return messaging;
		};

		for (int i = 0; i < 5; i++) {
			gateway.send(List.of("a", "b"), new MobilePushMessage("t", "b", Map.of()));
		}

		assertThat(rebuilt[0]).isFalse();
	}

	@Test
	@DisplayName("갈아 끼운 직후에는 쿨다운 동안 다시 갈아 끼우지 않는다")
	void 쿨다운_동안은_다시_갈아_끼우지_않는다() {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)));
		int[] rebuilds = {0};
		gateway.rebuilder = () -> {
			rebuilds[0]++;
			return messaging;
		};

		for (int i = 0; i < 12; i++) {
			gateway.send(List.of("a"), new MobilePushMessage("t", "b", Map.of()));
		}

		assertThat(rebuilds[0]).isEqualTo(1);
	}

	@Test
	@DisplayName("응답 타임아웃도 연속 실패로 세어, 풀이 응답을 아예 안 주는 형태의 재발도 갈아 끼운다")
	void 타임아웃도_연속_실패로_센다() {
		FirebaseMessaging hung = mock(FirebaseMessaging.class);
		FirebaseMessaging fresh = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(hung);
		when(hung.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> SettableApiFuture.<BatchResponse>create());
		gateway.batchTimeout = Duration.ofMillis(20);
		gateway.rebuilder = () -> fresh;
		MobilePushMessage message = new MobilePushMessage("t", "b", Map.of());

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> gateway.send(List.of("a"), message)).isInstanceOf(IllegalStateException.class);
		}

		verify(notificationService).sendSchedulerWarningNotification(any(), any());
	}

	@Test
	@DisplayName("배치가 예외로 끝나는 형태(ExecutionException)도 연속 실패로 센다")
	void 배치_예외도_연속_실패로_센다() {
		FirebaseMessaging broken = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(broken);
		SettableApiFuture<BatchResponse> failed = SettableApiFuture.create();
		failed.setException(new IllegalStateException("Pool entry is not present in the set of leased entries"));
		when(broken.sendEachForMulticastAsync(any(MulticastMessage.class))).thenReturn(failed);
		int[] rebuilds = {0};
		gateway.rebuilder = () -> {
			rebuilds[0]++;
			return broken;
		};

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> gateway.send(List.of("a"), new MobilePushMessage("t", "b", Map.of())))
					.isInstanceOf(IllegalStateException.class);
		}

		assertThat(rebuilds[0]).isEqualTo(1);
	}

	@Test
	@DisplayName("성공이 한 번이라도 있으면 연속 카운트가 처음부터 다시 시작한다")
	void 성공하면_카운트가_리셋된다() {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		BatchResponse ok = mock(BatchResponse.class);
		when(ok.getSuccessCount()).thenReturn(1);
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(ok))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)));
		int[] rebuilds = {0};
		gateway.rebuilder = () -> {
			rebuilds[0]++;
			return messaging;
		};

		for (int i = 0; i < 5; i++) {
			gateway.send(List.of("a"), new MobilePushMessage("t", "b", Map.of()));
		}

		assertThat(rebuilds[0]).isZero(); // 2연속 - 성공 - 2연속: 3연속이 없다
	}

	@Test
	@DisplayName("재생성이 실패해도 디스코드로 알린다")
	void 재생성이_실패해도_알린다() {
		FirebaseMessaging messaging = mock(FirebaseMessaging.class);
		when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
		when(messaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
				.thenAnswer(invocation -> ApiFutures.immediateFuture(allFailed(null, 1)));
		gateway.rebuilder = () -> {
			throw new IllegalStateException("boom");
		};

		for (int i = 0; i < 3; i++) {
			gateway.send(List.of("a"), new MobilePushMessage("t", "b", Map.of()));
		}

		verify(notificationService).sendSchedulerWarningNotification(any(), org.mockito.ArgumentMatchers.contains("재생성도 실패"));
	}
}
