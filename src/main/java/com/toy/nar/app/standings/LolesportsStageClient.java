package com.toy.nar.app.standings;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * lolesports 가 나눈 스테이지(Swiss / Round 4 / Playoffs …)에 속한 경기 id 를 준다.
 *
 * <p>우리 {@code league_match.match_title} 의 블록명은 데마시아 컵에서 Round 4(0-2 팀 3개가 도는 풀리그)
 * 도 "스위스" 라서 제목만으로는 스테이지를 못 가른다. lolesports 는 Round 4 를 승패 0-0 으로 새로 시작하는
 * 별도 스테이지로 다루므로, 스위스 순위에는 "Swiss" 스테이지 경기만 넣는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LolesportsStageClient {

	private static final String HOST = "esports-api.lolesports.com";

	private final WebClient webClient;

	@Value("${lolesports.riot-api.key}")
	private String riotApiKey;

	/** 가장 최근 토너먼트의 getStandings 원문. 조회 실패면 비어 있다(스테이지 필터·대진 없이 계산한다). */
	public Optional<JsonNode> standings(String leagueId) {
		try {
			JsonNode tournaments = get("/persisted/gw/getTournamentsForLeague", "leagueId", leagueId);
			String tournamentId = latestTournamentId(tournaments);
			if (tournamentId == null) {
				return Optional.empty();
			}
			return Optional.ofNullable(get("/persisted/gw/getStandings", "tournamentId", tournamentId));
		} catch (Exception e) {
			log.warn("lolesports 스테이지 조회 실패 — 전체 경기로 계산한다: leagueId={} err={}", leagueId, e.getMessage());
			return Optional.empty();
		}
	}

	private JsonNode get(String path, String key, String value) {
		return webClient.get()
				.uri(uri -> uri.scheme("https").host(HOST).path(path)
						.queryParam("hl", "en-US").queryParam(key, value).build())
				.header("x-api-key", riotApiKey)
				.header("Referer", "https://lolesports.com/")
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();
	}

	static String latestTournamentId(JsonNode root) {
		String id = null;
		String latestStart = "";
		for (JsonNode league : root.path("data").path("leagues")) {
			for (JsonNode t : league.path("tournaments")) {
				String start = t.path("startDate").asText("");
				if (start.compareTo(latestStart) >= 0) {
					latestStart = start;
					id = t.path("id").asText(null);
				}
			}
		}
		return id;
	}

	public static Optional<Set<String>> parseStageMatchIds(JsonNode root, String stageName) {
		for (JsonNode standing : root.path("data").path("standings")) {
			for (JsonNode stage : standing.path("stages")) {
				if (!stageName.equalsIgnoreCase(stage.path("name").asText())) {
					continue;
				}
				Set<String> ids = new HashSet<>();
				for (JsonNode section : stage.path("sections")) {
					for (JsonNode match : section.path("matches")) {
						ids.add(match.path("id").asText());
					}
				}
				return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
			}
		}
		return Optional.empty();
	}
}
