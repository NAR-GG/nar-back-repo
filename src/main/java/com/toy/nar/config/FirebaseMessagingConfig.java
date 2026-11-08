package com.toy.nar.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.internal.ApacheHttp2Transport;
import com.google.firebase.messaging.FirebaseMessaging;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class FirebaseMessagingConfig {

	private static final int FCM_MAX_CONNECTIONS = 2;
	private static final int FCM_CONNECT_TIMEOUT_MS = 10_000;
	private static final int FCM_READ_TIMEOUT_MS = 30_000;

	@Bean
	@ConditionalOnProperty(
			prefix = "firebase.messaging",
			name = "enabled",
			havingValue = "true")
	public FirebaseApp firebaseApp() throws IOException {
		List<FirebaseApp> apps = FirebaseApp.getApps();
		if (!apps.isEmpty()) {
			return apps.get(0);
		}
		// transport 는 만드는 순간 IO 스레드를 띄우므로 앱을 새로 만들 때만 만든다.
		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.getApplicationDefault())
				.setHttpTransport(fcmHttpTransport())
				// SDK 기본값은 0(무한)이다. read timeout 은 HTTP/2 공유 연결의 무활동 타이머라 죽은 연결만
				// 걸러낸다 — 요청별 상한이 아니다. 발송 스레드를 돌려받는 상한은 게이트웨이의 batchTimeout 이다
				// (2026-11-02 스케줄러 22시간 정지, FirebaseMobilePushGateway 참고).
				.setConnectTimeout(FCM_CONNECT_TIMEOUT_MS)
				.setReadTimeout(FCM_READ_TIMEOUT_MS)
				.build();
		return FirebaseApp.initializeApp(options);
	}

	/**
	 * FCM 으로 가는 HTTP 클라이언트. SDK 기본값을 그대로 두되 연결을 2개로 묶고 HTTP/2 로 겹쳐 쓴다.
	 *
	 * <p>SDK 기본 클라이언트는 HTTP/2 를 협상하고도 요청마다 연결을 따로 잡아, 일꾼 100명이 동시에
	 * 보내면 TCP·TLS 연결 100개를 한꺼번에 연다. 쉬는 동안 그 연결들이 죽고(수 분), 다음 발송이 100개를
	 * 다시 한꺼번에 열면 일부가 TCP 재시도 백오프에 빠진다. 이게 "세트 알림 첫 팬아웃만 20~50초" 의
	 * 정체였다(세트 시작 직전엔 FCM 공백이 ~25분).</p>
	 *
	 * <p>2026-10-25 실측(같은 이미지 임시 파드, dry-run 268건, 가장 느린 요청):
	 * 기본값은 200초 쉰 뒤 5.7~11.8초, 600초 쉰 뒤 36초. 연결 2개 + 멀티플렉싱은 어느 조건에서도 7초 이하.
	 * TCP keepalive·유휴 연결 정리(evictIdleConnections)·연결 100개 그대로 멀티플렉싱은 36초가 남아 기각.</p>
	 *
	 * <p>FCM 은 요청 1개에 토큰 1개라 동시 요청은 여전히 최대 100개(SDK 스레드풀)이고, 그게 연결
	 * 2개의 스트림으로 실린다.</p>
	 */
	static ApacheHttp2Transport fcmHttpTransport() {
		return new ApacheHttp2Transport(ApacheHttp2Transport.defaultHttpAsyncClientBuilder()
				.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
						.setMaxConnTotal(FCM_MAX_CONNECTIONS)
						.setMaxConnPerRoute(FCM_MAX_CONNECTIONS)
						.setMessageMultiplexing(true)
						// 아래 둘은 SDK 기본값(defaultHttpAsyncClientBuilder)과 같다 — 연결 관리자를 갈아끼우면 같이 사라진다.
						.setDefaultTlsConfig(TlsConfig.custom().setVersionPolicy(HttpVersionPolicy.NEGOTIATE).build())
						.setTlsStrategy(ClientTlsStrategyBuilder.create()
								.setSslContext(SSLContexts.createSystemDefault())
								.build())
						.build())
				.build());
	}

	@Bean
	@ConditionalOnProperty(
			prefix = "firebase.messaging",
			name = "enabled",
			havingValue = "true")
	public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
		return FirebaseMessaging.getInstance(firebaseApp);
	}

	private static final AtomicInteger GENERATION = new AtomicInteger();
	private static volatile String activeAppName = FirebaseApp.DEFAULT_APP_NAME;

	/**
	 * 연결 풀이 망가진 FCM 클라이언트를 버리고 새 앱(= 새 transport = 새 연결 풀)으로 갈아 끼운다.
	 * 2026-11-07 08:46 에 httpclient5 풀이 꼬여 이후 40시간 푸시가 전량 실패했고 파드 재시작만이 풀었다 —
	 * 게이트웨이가 연속 실패를 보고 이걸 부른다(FirebaseMobilePushGateway.recordInfraFailure).
	 * 옛 앱은 정리를 백그라운드로 미룬다: 망가진 풀의 shutdown 이 오래 걸려도 발송 스레드를 잡지 않게.
	 */
	public static synchronized FirebaseMessaging rebuildMessaging() {
		FirebaseApp old = FirebaseApp.getInstance(activeAppName);
		FirebaseOptions fresh = old.getOptions().toBuilder().setHttpTransport(fcmHttpTransport()).build();
		FirebaseApp next = FirebaseApp.initializeApp(fresh, "fcm-" + GENERATION.incrementAndGet());
		activeAppName = next.getName();
		Thread.ofVirtual().name("fcm-old-client-cleanup").start(() -> {
			try {
				old.getOptions().getHttpTransport().shutdown();
			} catch (Exception ignored) {
				// 이미 망가진 풀이라 실패해도 상관없다.
			}
			old.delete();
		});
		return FirebaseMessaging.getInstance(next);
	}
}
