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
	private static final int WORLDS_ADVANCE_WINS = 3;
	private static final int WORLDS_ELIMINATE_LOSSES = 3;

	private StandingsBracketBuilder() {
	}

	/** Playoffs 를 뺀 모든 스테이지 경기가 끝났는지. 8강 진출 8팀이 확정되는 시점이다. */
	static boolean groupStagesFinished(JsonNode root) {
		return groupStagesFinished(root, PLAYOFFS);
	}

	/** 토너먼트 스테이지 이름이 다른 대회(월즈: Knockouts)용. */
	static boolean groupStagesFinished(JsonNode root, String playoffsStage) {
		boolean any = false;
		for (JsonNode stage : stages(root)) {
			if (playoffsStage.equalsIgnoreCase(stage.path("name").asText())) {
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
		List<JsonNode> matches = stageMatches(root, PLAYOFFS);
		if (!anyTeam(matches)) {
			return Optional.empty();
		}
		return Optional.of(Bracket.builder()
				.swiss(swissRows(swissMetrics, teamInfo))
				.rounds(rounds(matches, scheduledTimes))
				.build());
	}

	/**
	 * 월즈 대진. 스위스는 3승 진출·3패 탈락이고, 시작하자마자(0-0 포함 16팀) 전적 카드를 내린다.
	 * 녹아웃은 팀이 한 곳이라도 차야 나온다 — 그 전엔 rounds 가 비어 앱이 스위스만 그린다.
	 */
	static Optional<Bracket> buildWorlds(JsonNode root, Map<String, TeamMetrics> swissMetrics,
			Map<String, String[]> teamInfo, Map<String, OffsetDateTime> scheduledTimes) {
		List<JsonNode> matches = stageMatches(root, WorldsStandingsBuilder.KNOCKOUTS);
		List<SwissRow> swiss = swissRows(swissMetrics, teamInfo, WORLDS_ADVANCE_WINS, WORLDS_ELIMINATE_LOSSES, true);
		List<Round> rounds = anyTeam(matches) ? rounds(matches, scheduledTimes) : List.of();
		if (swiss.isEmpty() && rounds.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(Bracket.builder().swiss(swiss).rounds(rounds).build());
	}

	private static List<JsonNode> stageMatches(JsonNode root, String stageName) {
		List<JsonNode> matches = new ArrayList<>();
		for (JsonNode stage : stages(root)) {
			if (!stageName.equalsIgnoreCase(stage.path("name").asText())) {
				continue;
			}
			for (JsonNode section : stage.path("sections")) {
				section.path("matches").forEach(matches::add);
			}
		}
		return matches;
	}

	private static boolean anyTeam(List<JsonNode> matches) {
		return matches.stream().anyMatch(m -> {
			for (JsonNode t : m.path("teams")) {
				if (!isTbd(t)) {
					return true;
				}
			}
			return false;
		});
	}

	static List<SwissRow> swissRows(Map<String, TeamMetrics> metrics, Map<String, String[]> teamInfo) {
		return swissRows(metrics, teamInfo, 2, 0, false);
	}

	/**
	 * @param advanceWins     이 승수 이상이면 진출
	 * @param eliminateLosses 이 패수 이상이면 탈락, 그 사이는 ACTIVE. 0 이면 옛 규칙(진출 아니면 전부 탈락 라벨)
	 * @param includeUnplayed 0-0 팀도 "0-0" 버킷으로 담는다(스위스 시작 직후)
	 */
	static List<SwissRow> swissRows(Map<String, TeamMetrics> metrics, Map<String, String[]> teamInfo,
			int advanceWins, int eliminateLosses, boolean includeUnplayed) {
		// 진출 → 진행 → 탈락, 그 안에서는 승 많은 순 → 패 적은 순. 같은 전적은 한 줄로 묶는다.
		Comparator<String> byRecord = Comparator
				.comparingInt((String r) -> statusRank(statusOf(r, advanceWins, eliminateLosses)))
				.thenComparingInt(r -> -Integer.parseInt(r.split("-")[0]))
				.thenComparingInt(r -> Integer.parseInt(r.split("-")[1]));
		Map<String, List<String>> byRecordMap = new TreeMap<>(byRecord);
		metrics.forEach((code, m) -> {
			if (includeUnplayed || m.wins() + m.losses() > 0) {
				byRecordMap.computeIfAbsent(m.wins() + "-" + m.losses(), k -> new ArrayList<>()).add(code);
			}
		});
		List<SwissRow> rows = new ArrayList<>();
		byRecordMap.forEach((record, codes) -> {
			codes.sort(Comparator.naturalOrder());
			// ponytail: 옛 규칙(데마시아 컵)은 2승 = 진출, 나머지는 bool 하나라 전부 탈락 라벨. 월즈는 status 로 진행을 구분한다.
			String status = statusOf(record, advanceWins, eliminateLosses);
			rows.add(SwissRow.builder().record(record).teamCodes(codes)
					.teams(codes.stream().map(c -> swissTeam(c, teamInfo.get(c))).toList())
					.advanced("ADVANCED".equals(status)).status(status).build());
		});
		return rows;
	}

	private static String statusOf(String record, int advanceWins, int eliminateLosses) {
		String[] wl = record.split("-");
		if (Integer.parseInt(wl[0]) >= advanceWins) {
			return "ADVANCED";
		}
		return eliminateLosses == 0 || Integer.parseInt(wl[1]) >= eliminateLosses ? "ELIMINATED" : "ACTIVE";
	}

	private static int statusRank(String status) {
		return switch (status) {
			case "ADVANCED" -> 0;
			case "ACTIVE" -> 1;
			default -> 2;
		};
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
