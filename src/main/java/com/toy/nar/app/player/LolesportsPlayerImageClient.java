package com.toy.nar.app.player;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LoL Esports getTeams API에서 선수 공식 프로필 이미지 URL을 수집한다.
 * 응답의 summonerName(소문자 트림) -> image URL 맵을 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LolesportsPlayerImageClient {

	private static final String DEFAULT_HEADSHOT = "default-headshot";

	private final WebClient webClient;

	@Value("${lolesports.riot-api.key}")
	private String apiKey;

	/**
	 * 전체 팀 로스터에서 선수명 -> 이미지 URL 맵 조회. 실패 시 빈 맵.
	 */
	public Map<String, String> fetchPlayerImages() {
		try {
			JsonNode root = webClient.get()
					.uri(uri -> uri
							.scheme("https")
							.host("esports-api.lolesports.com")
							.path("/persisted/gw/getTeams")
							.queryParam("hl", "ko-KR")
							.build())
					.header("x-api-key", apiKey)
					.header("Referer", "https://lolesports.com/")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
			return extractPlayerImages(root);
		} catch (Exception e) {
			log.error("Failed to fetch player images from LoL Esports getTeams API", e);
			return Map.of();
		}
	}

	/**
	 * getTeams 응답에서 선수명(소문자) -> 이미지 URL 맵 추출.
	 * default-headshot은 제외하고, 동명 선수는 LCK 소속팀 항목을 우선한다.
	 */
	static Map<String, String> extractPlayerImages(JsonNode root) {
		Map<String, String> images = new HashMap<>();
		if (root == null) {
			return images;
		}
		Map<String, Boolean> fromLck = new HashMap<>();

		for (JsonNode team : root.path("data").path("teams")) {
			boolean isLck = "LCK".equalsIgnoreCase(team.path("homeLeague").path("name").asText());
			for (JsonNode player : team.path("players")) {
				String name = player.path("summonerName").asText("").trim();
				String image = player.path("image").asText("");
				if (name.isEmpty() || image.isEmpty() || image.contains(DEFAULT_HEADSHOT)) {
					continue;
				}
				String key = name.toLowerCase(Locale.ROOT);
				if (!images.containsKey(key) || (isLck && !fromLck.getOrDefault(key, false))) {
					images.put(key, image);
					fromLck.put(key, isLck);
				}
			}
		}
		return images;
	}
}
