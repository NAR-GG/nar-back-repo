package com.toy.nar.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpServer;
import com.toy.nar.common.util.YoutubeApiProperties;

/**
 * 유튜브 WebClient 가 부트 기본 버퍼(256KB)보다 큰 응답을 받는지 잠근다.
 * 이 한도에 걸리면 예외가 채널 단위 catch 에 삼켜져 동기화만 조용히 빠진다.
 */
class YoutubeConfigTest {

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	@DisplayName("256KB 를 넘는 응답도 버퍼 한도에 걸리지 않고 읽는다")
	void readsResponseLargerThanBootDefaultBuffer() throws Exception {
		byte[] body = ("{\"description\":\"" + "a".repeat(600 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/videos", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		YoutubeApiProperties props = new YoutubeApiProperties();
		props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
		WebClient client = new YoutubeConfig().youtubeWebClient(props, WebClient.builder(), 4 * 1024 * 1024);

		String response = client.get().uri("/videos").retrieve().bodyToMono(String.class).block();

		assertThat(response).hasSize(body.length);
	}
}
