package com.toy.nar.app.standings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;

/**
 * 월즈 순위를 lolesports {@code getStandings} 스테이지 데이터로만 만든다(Play-Ins → Swiss → Knockouts).
 *
 * <p>우리 DB 를 안 쓰는 이유 — 경기 제목의 블록명이 월즈에서 어떻게 들어오는지 데이터가 오기 전에는
 * 알 수 없고, lolesports 가 스테이지·승패·세트를 이미 갖고 있다.
 */
final class WorldsStandingsBuilder {

	static final String PLAY_INS = "Play-Ins";
	static final String SWISS = "Swiss";
	static final String KNOCKOUTS = "Knockouts";

	private WorldsStandingsBuilder() {
	}

	/** 지금 표로 보여줄 스테이지와 라벨. */
	record Pick(String stage, String label) {
	}

	/**
	 * 진행 중인 스테이지를 고른다.
	 * <ul>
	 * <li>스위스 — 한 경기라도 시작했거나, 플레이-인이 전부 끝나고 스위스 대진이 채워졌거나, 녹아웃에 팀이 들어왔을 때.
	 * 플레이-인이 안 끝났는데 시드 팀만 먼저 찬 스위스 대진이 플레이-인 표를 가리지 않게 한다.</li>
	 * <li>플레이-인 — 그 전까지 팀이 있으면.</li>
	 * </ul>
	 */
	static Optional<Pick> activeStage(JsonNode root) {
		JsonNode playIns = stage(root, PLAY_INS);
		JsonNode swiss = stage(root, SWISS);
		JsonNode knockouts = stage(root, KNOCKOUTS);
		if (hasTeam(knockouts) || anyStarted(swiss) || (allCompleted(playIns) && hasTeam(swiss))) {
			return Optional.of(new Pick(SWISS, "스위스 스테이지"));
		}
		if (hasTeam(playIns)) {
			return Optional.of(new Pick(PLAY_INS, "플레이-인"));
		}
		return Optional.empty();
	}

	static JsonNode stage(JsonNode root, String name) {
		for (JsonNode standing : root.path("data").path("standings")) {
			for (JsonNode stage : standing.path("stages")) {
				if (name.equalsIgnoreCase(stage.path("name").asText())) {
					return stage;
				}
			}
		}
		return null;
	}

	/**
	 * 스테이지 경기 결과로 팀별 지표를 센다. 경기는 응답 순서(시간순)로 읽는다. 팀 이름·로고는 {@code info} 에 채운다
	 * ({이름, 로고 URL}). 대진이 확정된 팀은 0-0 이어도 담긴다.
	 */
	static Map<String, TeamMetrics> metrics(JsonNode stage, Map<String, String[]> info) {
		Map<String, int[]> acc = new LinkedHashMap<>(); // wins, losses, setWins, setLosses, streak, remaining
		if (stage != null) {
			for (JsonNode section : stage.path("sections")) {
				for (JsonNode m : section.path("matches")) {
					apply(m, acc, info);
				}
			}
		}
		Map<String, TeamMetrics> out = new LinkedHashMap<>();
		acc.forEach((code, a) -> out.put(code, new TeamMetrics(a[0], a[1], a[2], a[3], a[4], a[5])));
		return out;
	}

	private static void apply(JsonNode match, Map<String, int[]> acc, Map<String, String[]> info) {
		JsonNode teams = match.path("teams");
		List<String> codes = new ArrayList<>();
		for (JsonNode t : teams) {
			if (isTbd(t)) {
				continue;
			}
			String code = t.path("code").asText();
			codes.add(code);
			acc.computeIfAbsent(code, k -> new int[6]);
			info.putIfAbsent(code, new String[] { t.path("name").asText(code), httpsImage(t.path("image").asText(null)) });
		}
		if (codes.size() != 2) {
			return; // 상대가 아직 TBD — 어느 팀의 잔여 경기로 세지 않는다
		}
		if (!"completed".equals(match.path("state").asText())) {
			codes.forEach(c -> acc.get(c)[5]++);
			return;
		}
		for (int i = 0; i < 2; i++) {
			JsonNode me = teams.path(i);
			JsonNode other = teams.path(1 - i);
			if (me.path("result").isMissingNode() || me.path("result").isNull()) {
				continue;
			}
			int[] a = acc.get(me.path("code").asText());
			boolean win = "win".equals(me.path("result").path("outcome").asText());
			a[win ? 0 : 1]++;
			a[2] += me.path("result").path("gameWins").asInt();
			a[3] += other.path("result").path("gameWins").asInt();
			a[4] = win ? Math.max(a[4], 0) + 1 : Math.min(a[4], 0) - 1;
		}
	}

	private static String httpsImage(String url) {
		return url != null && url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
	}

	private static boolean hasTeam(JsonNode stage) {
		return stage != null && matches(stage).stream().anyMatch(m -> {
			for (JsonNode t : m.path("teams")) {
				if (!isTbd(t)) {
					return true;
				}
			}
			return false;
		});
	}

	private static boolean anyStarted(JsonNode stage) {
		return stage != null && matches(stage).stream().anyMatch(m -> !"unstarted".equals(m.path("state").asText()));
	}

	private static boolean allCompleted(JsonNode stage) {
		if (stage == null) {
			return false;
		}
		List<JsonNode> ms = matches(stage);
		return !ms.isEmpty() && ms.stream().allMatch(m -> "completed".equals(m.path("state").asText()));
	}

	private static List<JsonNode> matches(JsonNode stage) {
		List<JsonNode> out = new ArrayList<>();
		for (JsonNode section : stage.path("sections")) {
			section.path("matches").forEach(out::add);
		}
		return out;
	}

	private static boolean isTbd(JsonNode t) {
		String code = t.path("code").asText("");
		return code.isBlank() || "TBD".equalsIgnoreCase(code);
	}
}
