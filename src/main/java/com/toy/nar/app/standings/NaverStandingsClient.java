package com.toy.nar.app.standings;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 e스포츠 순위 API 클라이언트.
 *
 * <p>순위·승패·세트 득실차를 여기서 받는다. lolesports 를 쓰지 않는 이유는 스코프다 —
 * lolesports 는 LCK 를 Split 단위로 끊어 주는데(Split 3 만 보면 GEN 5승 2패), 유저가 실제로 보는
 * 네이버 화면은 정규 통산(GEN 19승 6패)이다. 숫자가 다르면 "순위 틀렸다" 문의가 온다.
 *
 * <p>동률 처리도 네이버가 쥐고 있다. 정렬은 승률 → 세트 득실차 → 그래도 같으면 공동 순위이고,
 * 이 규칙은 문서화된 게 아니라 관측으로 확인한 것이다(LPL·LEC 실측). 그래서 <b>우리가 재정렬하지
 * 않고 응답의 {@code rank} 를 그대로 쓴다.</b>
 *
 * <p>무인증이지만 {@code User-Agent} 가 없으면 막힌다. 우리는 이미
 * {@code NaverEsportsScoreClient} 로 같은 호스트에 의존하고 있어 새로 생기는 리스크는 아니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NaverStandingsClient {

	private static final String HOST = "esports-api.game.naver.com";
	private static final String UA = "Mozilla/5.0";
	private static final Duration TIMEOUT = Duration.ofSeconds(4);
	/** 시즌 목록은 거의 안 바뀐다. 순위 캐시(5분)와 달리 길게 잡는다. */
	private static final Duration LEAGUE_ID_TTL = Duration.ofHours(6);

	private final WebClient webClient;
	private final Map<String, CachedLeagueId> leagueIdCache = new ConcurrentHashMap<>();

	/** 네이버 순위 한 줄. {@code groupName} 은 그룹이 없는 리그면 null 이다. */
	public record NaverRankRow(
			String teamCode,
			String teamName,
			String imageUrl,
			String groupName,
			int rank,
			int wins,
			int losses,
			int setDiff) {
	}

	/**
	 * {@code topLeagueId}(lck, lec …)에 해당하는 <b>지금 진행 중인</b> 시즌 id 를 찾는다.
	 *
	 * <p>시즌 slug 가 해마다 바뀌므로(lec_2026_summer → …) 하드코딩하지 않고 기간으로 고른다.
	 * 후보가 여럿이면 기간이 긴 쪽을 쓴다 — LCK 는 하루짜리 {@code lck_2026_event}(이벤트 매치)가
	 * 같은 {@code topLeagueId} 로 끼어 있어서, 그날 하루는 후보가 둘이 된다.
	 */
	public Optional<String> resolveLeagueId(String topLeagueId) {
		CachedLeagueId cached = leagueIdCache.get(topLeagueId);
		if (cached != null && cached.fetchedAt().plus(LEAGUE_ID_TTL).isAfter(Instant.now())) {
			return Optional.ofNullable(cached.leagueId());
		}
		Optional<String> resolved = fetchLeagueId(topLeagueId);
		resolved.ifPresent(id -> leagueIdCache.put(topLeagueId, new CachedLeagueId(Instant.now(), id)));
		return resolved;
	}

	private Optional<String> fetchLeagueId(String topLeagueId) {
		JsonNode root = get("/service/v1/meta/leagues");
		if (root == null) {
			return Optional.empty();
		}
		long now = System.currentTimeMillis();
		JsonNode best = null;
		long bestSpan = -1;
		for (JsonNode league : root.path("content")) {
			if (!"lol".equals(league.path("gameCode").asText())
					|| !topLeagueId.equals(league.path("topLeagueId").asText())) {
				continue;
			}
			long start = league.path("startDate").asLong();
			long end = league.path("endDate").asLong();
			if (start > now || end < now) {
				continue;
			}
			long span = end - start;
			if (span > bestSpan) {
				bestSpan = span;
				best = league;
			}
		}
		if (best == null) {
			log.info("네이버에 진행 중인 시즌이 없다: topLeagueId={}", topLeagueId);
			return Optional.empty();
		}
		return Optional.of(best.path("leagueId").asText());
	}

	/** 순위 행. 실패하면 빈 목록을 준다 — 순위표가 없는 것과 조회 실패를 서비스에서 구분한다. */
	public List<NaverRankRow> fetchRanking(String leagueId) {
		JsonNode root = get("/service/v1/ranking/" + leagueId + "/team");
		if (root == null) {
			return List.of();
		}
		List<NaverRankRow> rows = new ArrayList<>();
		for (JsonNode row : root.path("content")) {
			JsonNode team = row.path("team");
			String code = team.path("nameEngAcronym").asText("");
			if (code.isBlank()) {
				continue;
			}
			rows.add(new NaverRankRow(
					code,
					team.path("name").asText(code),
					emptyToNull(team.path("imageUrl").asText("")),
					emptyToNull(row.path("groupName").asText("")),
					row.path("rank").asInt(),
					row.path("wins").asInt(),
					row.path("loses").asInt(),
					// 필드명은 score 지만 실제로는 세트 득실차다. LCK 10팀·LPL 12팀을
					// 우리 DB 합산과 대조해 확인했다.
					row.path("score").asInt()));
		}
		rows.sort(Comparator.comparingInt(NaverRankRow::rank));
		return rows;
	}

	private JsonNode get(String path) {
		try {
			return webClient.get()
					.uri(uri -> uri.scheme("https").host(HOST).path(path).build())
					.header("User-Agent", UA)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.timeout(TIMEOUT)
					.block();
		} catch (Exception e) {
			log.warn("네이버 순위 API 호출 실패: path={} err={}", path, e.getMessage());
			return null;
		}
	}

	private static String emptyToNull(String v) {
		return v == null || v.isBlank() ? null : v;
	}

	private record CachedLeagueId(Instant fetchedAt, String leagueId) {
	}
}
