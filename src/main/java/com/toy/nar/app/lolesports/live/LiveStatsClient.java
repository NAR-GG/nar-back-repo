package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStatsClient {

	private final WebClient webClient;

	@Value("${lolesports.riot-api.key}")
	private String riotApiKey;

	@Value("${lolesports.live.request-timeout-ms:3000}")
	private long requestTimeoutMs;

	public JsonNode getWindow(String gameId, String startingTime) {
		return call(gameId, startingTime, "window");
	}

	public JsonNode getDetails(String gameId, String startingTime) {
		return call(gameId, startingTime, "details");
	}

	private JsonNode call(String gameId, String startingTime, String endpoint) {
		return webClient.get()
				.uri(uriBuilder -> uriBuilder
						.scheme("https")
						.host("feed.lolesports.com")
						.path("/livestats/v1/" + endpoint + "/{gameId}")
						.queryParam("startingTime", startingTime)
						.build(gameId))
				.header("x-api-key", riotApiKey)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.timeout(Duration.ofMillis(requestTimeoutMs))
				.block();
	}
}

