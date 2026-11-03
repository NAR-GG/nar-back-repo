package com.toy.nar.app.mobile.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseMobilePushGateway implements MobilePushGateway {

	private static final int MAX_MULTICAST_TOKENS = 500;

	/**
	 * 이만큼 쉬었으면 첫 토큰 1건만 먼저 보내 연결을 열고, 나머지를 그 연결에 싣는다.
	 * 연결이 없을 때(오래 쉬어 닫힌 뒤) 몰려온 요청은 연결을 나눠 쓰지 못하고 한 건씩 풀려
	 * 토큰당 ~0.1초가 든다 — 2026-10-26 실측에서 180초 넘게 쉰 뒤 첫 묶음만 27~49초,
	 * 60~170초 쉰 뒤는 정상. 여유를 두고 60초로 잡는다. 비용은 왕복 1회.
	 * 토큰이 연결 상한(FCM_MAX_CONNECTIONS=2) 이하면 기다릴 요청이 없어 건너뛴다.
	 */
	private static final long WARMUP_IDLE_MS = 60_000;

	/**
	 * 묶음 하나를 기다리는 상한. <b>발송 스레드가 반드시 돌아오게 하는 건 이것뿐이다</b> —
	 * read timeout(FirebaseMessagingConfig)은 HTTP/2 공유 연결의 무활동 타이머라 같은 연결에
	 * 다른 응답이 오가면 리셋돼서, 죽은 연결만 걸러낸다.
	 * 2026-11-02 16:08 에 묶음 하나가 영영 안 끝나 그 스레드를 쓰던 fixedDelay 잡 전부(솔랭 알림·
	 * 라이브 폴링)가 22시간 섰다. 정상 묶음(500건)은 2초 안팎, 콜드 최악 실측 49초라 넉넉히 잡는다.
	 */
	Duration batchTimeout = Duration.ofSeconds(120);

	private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

	/**
	 * 발송 소요 진단용. 세트 알림의 첫 팬아웃만 규모와 무관하게 20~50초 걸리는데(2026-10-25 실측)
	 * DB·CPU·DNS 는 결백했다. 남은 후보는 FCM HTTP 계층 — 오래 쉰 뒤 첫 발송인지(idleBeforeMs),
	 * 다른 발송과 SDK 스레드풀(100)을 나눠 쓰는 중인지(concurrentSends)를 묶음별 소요와 함께 남긴다.
	 * idleBeforeMs 는 "마지막으로 끝난 발송" 기준이라 concurrentSends > 0 이면 연결이 쓰이는 중인데도
	 * 크게 나올 수 있다 — 두 값을 같이 읽는다. SDK 가 요청별 소요를 주지 않아 묶음 소요로 대신한다.
	 */
	private final AtomicInteger inFlightSends = new AtomicInteger();
	private final AtomicLong lastSendFinishedAtMs = new AtomicLong();

	@Override
	public boolean isAvailable() {
		return firebaseMessagingProvider.getIfAvailable() != null;
	}

	@Override
	public MobilePushResult send(List<String> tokens, MobilePushMessage message) {
		FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
		if (firebaseMessaging == null) {
			throw new IllegalStateException("FCM 발송이 비활성화되어 있습니다.");
		}

		int successCount = 0;
		int failureCount = 0;
		List<String> invalidTokens = new ArrayList<>();
		List<String> successTokens = new ArrayList<>();
		List<Long> batchMillis = new ArrayList<>();
		long lastFinishedAt = lastSendFinishedAtMs.get();
		long idleBeforeMs = lastFinishedAt == 0 ? -1 : System.currentTimeMillis() - lastFinishedAt;
		int concurrentSends = inFlightSends.getAndIncrement();
		boolean warmup = tokens.size() > 2 && (idleBeforeMs < 0 || idleBeforeMs >= WARMUP_IDLE_MS);
		long startedAt = System.nanoTime();
		try {
			for (int start = 0; start < tokens.size(); ) {
				int end = warmup && start == 0 ? 1 : Math.min(start + MAX_MULTICAST_TOKENS, tokens.size());
				List<String> batchTokens = tokens.subList(start, end);
				start = end;
				long batchStartedAt = System.nanoTime();
				BatchResponse response = sendBatch(firebaseMessaging, batchTokens, message);
				batchMillis.add(elapsedMillis(batchStartedAt));
				successCount += response.getSuccessCount();
				failureCount += response.getFailureCount();
				collectInvalidTokens(batchTokens, response.getResponses(), invalidTokens);
				collectSuccessTokens(batchTokens, response.getResponses(), successTokens);
			}
		} finally {
			inFlightSends.decrementAndGet();
			lastSendFinishedAtMs.set(System.currentTimeMillis());
			// 실패(예외)로 끝나도 남긴다 — 타임아웃이면 그 소요 자체가 단서다.
			log.info("[fcm-send] tokens={} success={} failure={} batchesMs={} totalMs={} idleBeforeMs={} concurrentSends={} warmup={}",
					tokens.size(), successCount, failureCount, batchMillis, elapsedMillis(startedAt),
					idleBeforeMs, concurrentSends, warmup);
		}
		return new MobilePushResult(
				successCount, failureCount, List.copyOf(invalidTokens), List.copyOf(successTokens));
	}

	private static long elapsedMillis(long startedAtNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
	}

	private BatchResponse sendBatch(
			FirebaseMessaging firebaseMessaging,
			List<String> tokens,
			MobilePushMessage message) {
		MulticastMessage multicastMessage = MulticastMessage.builder()
				.setNotification(Notification.builder()
						.setTitle(message.title())
						.setBody(message.body())
						.build())
				.putAllData(message.data())
				.setAndroidConfig(androidConfig())
				.setApnsConfig(apnsConfig())
				.addAllTokens(tokens)
				.build();
		Future<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(multicastMessage);
		boolean done = false;
		try {
			BatchResponse response = future.get(batchTimeout.toMillis(), TimeUnit.MILLISECONDS);
			done = true;
			return response;
		} catch (TimeoutException e) {
			// ponytail: 이 묶음의 일부, 그리고 500건 넘는 발송이면 앞서 성공한 묶음까지 호출부가 전원
			// FAILED 로 남기고 재예약하므로 두 번 받을 수 있다 — 스레드를 영영 잃는 것보다 낫다.
			// 잦아지면 성공한 묶음 결과는 살려서 돌려준다.
			throw new IllegalStateException(
					"FCM 멀티캐스트 응답이 " + batchTimeout.toSeconds() + "초 안에 오지 않았습니다.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("FCM 멀티캐스트 발송 대기 중 인터럽트되었습니다.", e);
		} catch (ExecutionException e) {
			done = true;
			throw new IllegalStateException("FCM 멀티캐스트 발송에 실패했습니다.", e.getCause());
		} finally {
			if (!done) {
				future.cancel(true);
			}
		}
	}

	/**
	 * 알림 잠자기는 무음 발송이 아니라 <b>발송 자체를 건너뛰는</b> 방식이라
	 * (QuietAwarePushSender 참고) 게이트웨이는 소리 나는 발송만 만든다.
	 */
	AndroidConfig androidConfig() {
		return AndroidConfig.builder()
				.setPriority(AndroidConfig.Priority.HIGH)
				.build();
	}

	ApnsConfig apnsConfig() {
		return ApnsConfig.builder()
				.setAps(Aps.builder().setSound("default").build())
				.build();
	}

	/** 여러 구독자의 토큰을 한 번에 보낼 때, 누가 받았는지 되돌리려면 성공 토큰이 필요하다. */
	private void collectSuccessTokens(
			List<String> tokens,
			List<SendResponse> responses,
			List<String> successTokens) {
		for (int index = 0; index < responses.size(); index++) {
			if (responses.get(index).isSuccessful()) {
				successTokens.add(tokens.get(index));
			}
		}
	}

	private void collectInvalidTokens(
			List<String> tokens,
			List<SendResponse> responses,
			List<String> invalidTokens) {
		for (int index = 0; index < responses.size(); index++) {
			SendResponse response = responses.get(index);
			if (response.isSuccessful() || response.getException() == null) {
				continue;
			}
			MessagingErrorCode errorCode = response.getException().getMessagingErrorCode();
			if (errorCode == MessagingErrorCode.UNREGISTERED
					|| errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
				invalidTokens.add(tokens.get(index));
			}
		}
	}
}
