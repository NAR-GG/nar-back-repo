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

@Configuration
public class FirebaseMessagingConfig {

	private static final int FCM_MAX_CONNECTIONS = 2;

	@Bean
	@ConditionalOnProperty(
			prefix = "firebase.messaging",
			name = "enabled",
			havingValue = "true")
	public FirebaseApp firebaseApp() throws IOException {
		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.getApplicationDefault())
				.setHttpTransport(fcmHttpTransport())
				.build();
		return FirebaseApp.getApps().stream()
				.findFirst()
				.orElseGet(() -> FirebaseApp.initializeApp(options));
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
}
