package com.toy.nar.app.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.domain.participant.LckTeamCatalog;
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
			return extractPlayerImages(fetchTeams());
		} catch (Exception e) {
			log.error("Failed to fetch player images from LoL Esports getTeams API", e);
			return Map.of();
		}
	}

	/**
	 * LCK 1군 팀 로스터에서 선수명(소문자) -> 팀 코드 맵 조회. 실패 시 빈 맵.
	 */
	public Map<String, String> fetchLckFirstTeamRosters() {
		try {
			return extractLckFirstTeamRosters(fetchTeams());
		} catch (Exception e) {
			log.error("Failed to fetch LCK rosters from LoL Esports getTeams API", e);
			return Map.of();
		}
	}

	private JsonNode fetchTeams() {
		return webClient.get()
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
	}

	/**
	 * getTeams 응답에서 LCK 1군 팀({@link LckTeamCatalog#TEAM_CODES})의 선수명(소문자) -> 팀 코드 맵 추출.
	 *
	 * <p>응답에는 해체된 LCK 팀까지 80개가 들어오고 그중 일부에 유령 로스터가 붙어 있다(2026-08 기준
	 * VSG·Seorabeol Gaming에 동일 선수 1명). 팀 코드를 1군 카탈로그로 좁혀 걸러낸다.
	 *
	 * <p>이 로스터는 1군/2군 구분이 없다 — LCK 팀과 같은 코드의 LCK 챌린저스 팀 로스터가 완전히 같은
	 * 집합이다(2026-08 기준 10팀 전부 일치). 그래서 여기서 나온 팀은 "소속 구단"으로만 쓰고 1군 여부의
	 * 근거로 삼지 않는다.
	 */
	static Map<String, String> extractLckFirstTeamRosters(JsonNode root) {
		Map<String, String> rosters = new HashMap<>();
		if (root == null) {
			return rosters;
		}

		for (JsonNode team : root.path("data").path("teams")) {
			String code = team.path("code").asText("");
			if (!LckTeamCatalog.contains(code)) {
				continue;
			}
			if (!"LCK".equalsIgnoreCase(team.path("homeLeague").path("name").asText())) {
				continue;
			}
			for (JsonNode player : team.path("players")) {
				String name = player.path("summonerName").asText("").trim();
				if (!name.isEmpty()) {
					rosters.put(name.toLowerCase(Locale.ROOT), code.toUpperCase(Locale.ROOT));
				}
			}
		}
		return rosters;
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
