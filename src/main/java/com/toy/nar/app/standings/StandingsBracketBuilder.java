package com.toy.nar.app.standings;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;
import com.toy.nar.app.standings.dto.StandingsResponse.Bracket;
import com.toy.nar.app.standings.dto.StandingsResponse.BracketMatch;
import com.toy.nar.app.standings.dto.StandingsResponse.BracketTeam;
import com.toy.nar.app.standings.dto.StandingsResponse.Round;
import com.toy.nar.app.standings.dto.StandingsResponse.SwissRow;
import com.toy.nar.app.standings.dto.StandingsResponse.SwissTeam;

/**
 * 데마시아 컵 대진 응답. 스위스 전적 버킷은 우리 DB 집계로, 토너먼트는 lolesports
 * {@code getStandings} 의 Playoffs 스테이지로 만든다.
 *
 * <p>lolesports 의 "Round 4"(0-2 팀 풀리그)·"Round 5"(공식 스위스 4라운드)는 대진 응답에 넣지 않는다 —
 * 그 결과는 8강 대진의 팀 확정으로 드러난다.
 */
final class StandingsBracketBuilder {

	private static final String PLAYOFFS = "Playoffs";

	private StandingsBracketBuilder() {
	}

	/** Playoffs 를 뺀 모든 스테이지 경기가 끝났는지. 8강 진출 8팀이 확정되는 시점이다. */
	static boolean groupStagesFinished(JsonNode root) {
		boolean any = false;
		for (JsonNode stage : stages(root)) {
			if (PLAYOFFS.equalsIgnoreCase(stage.path("name").asText())) {
				continue;
			}
			for (JsonNode section : stage.path("sections")) {
				for (JsonNode match : section.path("matches")) {
					any = true;
					if (!"completed".equals(match.path("state").asText())) {
						return false;
					}
				}
			}
		}
		return any;
	}

	/**
	 * 8강에 팀이 한 곳이라도 들어오기 전에는 비어 있다 — 그때까지는 앱이 순위표를 그린다.
	 * 대진 UI 는 월즈 기준이라 전부 TBD 인 카드 7장을 먼저 보여줄 이유가 없다.
	 */
	static Optional<Bracket> build(JsonNode root, Map<String, TeamMetrics> swissMetrics,
			Map<String, String[]> teamInfo, Map<String, OffsetDateTime> scheduledTimes) {
		List<JsonNode> matches = new ArrayList<>();
		for (JsonNode stage : stages(root)) {
			if (!PLAYOFFS.equalsIgnoreCase(stage.path("name").asText())) {
				continue;
			}
			for (JsonNode section : stage.path("sections")) {
				section.path("matches").forEach(matches::add);
			}
		}
		boolean anyTeam = matches.stream().anyMatch(m -> {
			for (JsonNode t : m.path("teams")) {
				if (!isTbd(t)) {
					return true;
				}
			}
			return false;
		});
		if (!anyTeam) {
			return Optional.empty();
		}
		return Optional.of(Bracket.builder()
				.swiss(swissRows(swissMetrics, teamInfo))
				.rounds(rounds(matches, scheduledTimes))
				.build());
	}

	static List<SwissRow> swissRows(Map<String, TeamMetrics> metrics, Map<String, String[]> teamInfo) {
		// 승 많은 순 → 패 적은 순. 같은 전적은 한 줄로 묶는다.
		Map<String, List<String>> byRecord = new TreeMap<>(Comparator
				.comparingInt((String r) -> -Integer.parseInt(r.split("-")[0]))
				.thenComparingInt(r -> Integer.parseInt(r.split("-")[1])));
		metrics.forEach((code, m) -> {
			if (m.wins() + m.losses() > 0) {
				byRecord.computeIfAbsent(m.wins() + "-" + m.losses(), k -> new ArrayList<>()).add(code);
			}
		});
		List<SwissRow> rows = new ArrayList<>();
		byRecord.forEach((record, codes) -> {
			codes.sort(Comparator.naturalOrder());
			// ponytail: 2승 = 진출 확정. 1-2·0-2 는 풀리그·4라운드가 남아 "진행 중"이지만 앱 필드는 bool 하나뿐이라 false.
			rows.add(SwissRow.builder().record(record).teamCodes(codes)
					.teams(codes.stream().map(c -> swissTeam(c, teamInfo.get(c))).toList())
					.advanced(Integer.parseInt(record.split("-")[0]) >= 2).build());
		});
		return rows;
	}

	/** 앱이 팀 코드로 로고를 못 찾는 해외팀도 그릴 수 있게 이름·로고를 같이 준다. info = {이름, 로고 URL}. */
	private static SwissTeam swissTeam(String code, String[] info) {
		return new SwissTeam(code, info != null && info[0] != null ? info[0] : code, info != null ? info[1] : null);
	}

	/** 7경기면 8강 4·4강 2·결승 1. lolesports 가 라운드를 안 줘서 경기 순서로 나눈다(id 오름차순 = 대진 순). */
	static List<Round> rounds(List<JsonNode> matches, Map<String, OffsetDateTime> scheduledTimes) {
		List<Round> rounds = new ArrayList<>();
		int from = 0;
		int remaining = matches.size();
		while (remaining > 0) {
			int size = (remaining + 1) / 2;
			boolean last = size == remaining && size == 1;
			List<BracketMatch> out = new ArrayList<>();
			for (JsonNode m : matches.subList(from, from + size)) {
				out.add(match(m, scheduledTimes, last));
			}
			rounds.add(Round.builder().name(roundName(size)).matches(out).build());
			from += size;
			remaining -= size;
		}
		return rounds;
	}

	private static String roundName(int size) {
		return size == 1 ? "결승" : size * 2 + "강";
	}

	private static BracketMatch match(JsonNode m, Map<String, OffsetDateTime> scheduledTimes, boolean isFinal) {
		JsonNode teams = m.path("teams");
		String id = m.path("id").asText();
		return BracketMatch.builder()
				.teamA(team(teams.path(0)))
				.teamB(team(teams.path(1)))
				.status(switch (m.path("state").asText()) {
					case "completed" -> "done";
					case "inProgress" -> "live";
					default -> "upcoming";
				})
				.matchId(id)
				.scheduledTime(scheduledTimes.get(id))
				.isFinal(isFinal)
				.build();
	}

	private static BracketTeam team(JsonNode t) {
		if (isTbd(t)) {
			return BracketTeam.builder().build();
		}
		JsonNode result = t.path("result");
		return BracketTeam.builder()
				.teamCode(t.path("code").asText())
				.teamName(t.path("name").asText(null))
				.imageUrl(t.path("image").asText(null))
				.gameWins(result.isMissingNode() || result.isNull() ? null : result.path("gameWins").asInt())
				.won(result.isMissingNode() || result.isNull() ? null : "win".equals(result.path("outcome").asText()))
				.build();
	}

	private static boolean isTbd(JsonNode t) {
		String code = t.path("code").asText("");
		return code.isBlank() || "TBD".equalsIgnoreCase(code);
	}

	private static Iterable<JsonNode> stages(JsonNode root) {
		List<JsonNode> out = new ArrayList<>();
		for (JsonNode standing : root.path("data").path("standings")) {
			standing.path("stages").forEach(out::add);
		}
		return out;
	}
}
