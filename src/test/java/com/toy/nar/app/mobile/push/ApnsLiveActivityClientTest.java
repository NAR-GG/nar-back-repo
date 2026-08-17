package com.toy.nar.app.mobile.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * APNs 발송의 동시 스트림 상한과 재시도를 검증한다.
 *
 * <p>2026-08-17 KeSPA T1 vs DNS 회귀 방지용이다. 팬아웃을 그대로 HttpClient 에 쏟으면
 * APNs 커넥션의 동시 스트림 한도(1,000)를 넘는 요청이 큐에 쌓이지 않고
 * {@code IOException: too many concurrent streams} 로 즉사했고, 그 실패가 "성공"으로 집계됐다.</p>
 */
class ApnsLiveActivityClientTest {

	/** {@code ApnsLiveActivityClient.MAX_IN_FLIGHT} 와 같아야 한다. */
	private static final int MAX_IN_FLIGHT = 500;

	private static Path cachedKeyFile;

	@Test
	void 일시_실패는_한_번_재시도해서_살린다() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HttpClient httpClient = mock(HttpClient.class);
		when(httpClient.sendAsync(any(), any())).thenAnswer(invocation -> calls.incrementAndGet() == 1
				? CompletableFuture.failedFuture(new IOException("too many concurrent streams"))
				: CompletableFuture.completedFuture(response(200, "")));

		ApnsLiveActivityClient client = client(httpClient);

		assertThat(client.sendUpdate("tok", contentState())).isTrue();
		assertThat(calls.get()).isEqualTo(2);
		assertThat(client.transientFailureCount()).isZero();
	}

	@Test
	void 재시도까지_실패하면_실패로_집계한다() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HttpClient httpClient = mock(HttpClient.class);
		when(httpClient.sendAsync(any(), any())).thenAnswer(invocation -> {
			calls.incrementAndGet();
			return CompletableFuture.failedFuture(new IOException("too many concurrent streams"));
		});

		ApnsLiveActivityClient client = client(httpClient);

		// 토큰 문제가 아니므로 살려 두지만(true), 조용히 성공으로 세지 않는다.
		assertThat(client.sendUpdate("tok", contentState())).isTrue();
		assertThat(calls.get()).isEqualTo(2);
		assertThat(client.transientFailureCount()).isEqualTo(1);
	}

	@Test
	void 죽은_토큰은_재시도하지_않는다() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HttpClient httpClient = mock(HttpClient.class);
		when(httpClient.sendAsync(any(), any())).thenAnswer(invocation -> {
			calls.incrementAndGet();
			return CompletableFuture.completedFuture(response(410, "{\"reason\":\"Unregistered\"}"));
		});

		ApnsLiveActivityClient client = client(httpClient);

		assertThat(client.sendUpdate("tok", contentState())).isFalse();
		assertThat(calls.get()).isEqualTo(1);
		assertThat(client.transientFailureCount()).isZero();
	}

	@Test
	void 상한을_넘는_팬아웃은_버리지_않고_대기시킨다() throws Exception {
		List<CompletableFuture<HttpResponse<String>>> dispatched =
				Collections.synchronizedList(new ArrayList<>());
		HttpClient httpClient = mock(HttpClient.class);
		when(httpClient.sendAsync(any(), any())).thenAnswer(invocation -> {
			CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
			dispatched.add(pending);
			return pending;
		});

		ApnsLiveActivityClient client = client(httpClient);
		int total = MAX_IN_FLIGHT + 5;
		Thread sender = new Thread(() -> {
			for (int i = 0; i < total; i++) {
				client.sendUpdateAsync("tok-" + i, contentState());
			}
		});
		sender.start();

		// 앞의 응답이 오기 전에는 상한까지만 나간다.
		waitUntil(() -> dispatched.size() >= MAX_IN_FLIGHT);
		Thread.sleep(100);
		assertThat(dispatched.size()).isEqualTo(MAX_IN_FLIGHT);
		assertThat(sender.isAlive()).isTrue();

		// 응답이 돌아오면 초과분도 빠짐없이 나간다 — 실패로 버려지지 않는다.
		long deadline = System.currentTimeMillis() + 10_000;
		while (dispatched.size() < total && System.currentTimeMillis() < deadline) {
			List.copyOf(dispatched).forEach(pending -> pending.complete(response(200, "")));
			Thread.sleep(10);
		}
		sender.join(5_000);
		assertThat(dispatched.size()).isEqualTo(total);
		assertThat(client.transientFailureCount()).isZero();
	}

	private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
	}

	private Map<String, Object> contentState() {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("phase", "playing");
		state.put("setNumber", 2);
		return state;
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> response(int statusCode, String body) {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(statusCode);
		when(response.body()).thenReturn(body);
		return response;
	}

	private ApnsLiveActivityClient client(HttpClient httpClient) throws Exception {
		ApnsLiveActivityClient client = new ApnsLiveActivityClient(httpClient);
		ReflectionTestUtils.setField(client, "enabled", true);
		ReflectionTestUtils.setField(client, "keyPath", keyFile().toString());
		ReflectionTestUtils.setField(client, "keyId", "TESTKEYID1");
		ReflectionTestUtils.setField(client, "teamId", "TESTTEAMID");
		ReflectionTestUtils.setField(client, "bundleId", "com.nar.test");
		ReflectionTestUtils.setField(client, "host", "https://api.push.apple.com");
		return client;
	}

	/** JWT 서명에 실제 EC 키가 필요하다. 발급 실패는 발송 자체를 건너뛰게 만들어 검증이 무의미해진다. */
	private static synchronized Path keyFile() throws Exception {
		if (cachedKeyFile != null) {
			return cachedKeyFile;
		}
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair pair = generator.generateKeyPair();
		Path file = Files.createTempFile("apns-test", ".p8");
		file.toFile().deleteOnExit();
		Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n");
		cachedKeyFile = file;
		return file;
	}
}
