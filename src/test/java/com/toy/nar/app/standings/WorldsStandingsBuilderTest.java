package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;
import com.toy.nar.app.standings.dto.StandingsResponse;

class WorldsStandingsBuilderTest {

	private static final String TBD = "{\"code\":\"TBD\",\"result\":null}";

	private static String team(String code, String outcome, int gw) {
		return "{\"code\":\"%s\",\"name\":\"%s FC\",\"image\":\"http://img/%s.png\",\"result\":{\"outcome\":\"%s\",\"gameWins\":%d}}"
				.formatted(code, code, code, outcome, gw);
	}

	private static String done(String id, String w, int wg, String l, int lg) {
		return "{\"id\":\"%s\",\"state\":\"completed\",\"teams\":[%s,%s]}".formatted(id, team(w, "win", wg), team(l, "loss", lg));
	}

	private static String open(String id, String state, String a, String b) {
		return "{\"id\":\"%s\",\"state\":\"%s\",\"teams\":[%s,%s]}".formatted(id, state, side(a), side(b));
	}

	private static String side(String code) {
		return TBD.equals(code) ? code : "{\"code\":\"%s\",\"name\":\"%s\",\"result\":null}".formatted(code, code);
	}

	private static String stage(String name, String... matches) {
		return "{\"name\":\"%s\",\"sections\":[{\"matches\":[%s]}]}".formatted(name, String.join(",", matches));
	}

	private static JsonNode root(String playIns, String swiss, String knockouts) throws Exception {
		return new ObjectMapper().readTree("{\"data\":{\"standings\":[{\"stages\":[%s,%s,%s]}]}}"
				.formatted(playIns, swiss, knockouts));
	}

	private static String tbdStage(String name, int n) {
		String[] ms = new String[n];
		for (int i = 0; i < n; i++) {
			ms[i] = open(name + i, "unstarted", TBD, TBD);
		}
		return stage(name, ms);
	}

	/** 2026-10-18 실측과 같은 플레이-인: MVK 3-0 FUR, KC 3-2 C9, KC 3-0 MVK, FUR 3-1 C9, 남은 2경기는 미정. */
	private static String playIns() {
		return stage("Play-Ins",
				done("p1", "MVK", 3, "FUR", 0), done("p2", "KC", 3, "C9", 2),
				done("p3", "KC", 3, "MVK", 0), done("p4", "FUR", 3, "C9", 1),
				open("p5", "unstarted", "MVK", "FUR"), open("p6", "unstarted", TBD, "KC"));
	}

	private static String stageOf(JsonNode r) {
		return WorldsStandingsBuilder.activeStage(r).orElseThrow().stage();
	}

	@DisplayName("플레이-인 진행 중에는 플레이-인 표 — 승패·세트·연속·잔여를 센다")
	@Test
	void playInsTableWhileSwissIsEmpty() throws Exception {
		JsonNode r = root(playIns(), tbdStage("Swiss", 3), tbdStage("Knockouts", 2));

		assertThat(stageOf(r)).isEqualTo("Play-Ins");

		Map<String, String[]> info = new LinkedHashMap<>();
		Map<String, TeamMetrics> m = WorldsStandingsBuilder.metrics(WorldsStandingsBuilder.stage(r, "Play-Ins"), info);
		// KC 의 마지막 경기는 상대가 TBD 라 잔여로 세지 않는다
		assertThat(m.get("KC")).isEqualTo(new TeamMetrics(2, 0, 6, 2, 2, 0));
		assertThat(m.get("MVK")).isEqualTo(new TeamMetrics(1, 1, 3, 3, -1, 1));
		assertThat(m.get("C9")).isEqualTo(new TeamMetrics(0, 2, 3, 6, -2, 0));
		assertThat(info.get("KC")[1]).isEqualTo("https://img/KC.png");
	}

	@DisplayName("스위스 대진이 시드 팀으로 먼저 차도 플레이-인이 안 끝났으면 플레이-인 표를 유지한다")
	@Test
	void seededSwissDoesNotHidePlayIns() throws Exception {
		JsonNode r = root(playIns(), stage("Swiss", open("s1", "unstarted", "T1", "GEN")), tbdStage("Knockouts", 2));

		assertThat(stageOf(r)).isEqualTo("Play-Ins");
	}

	@DisplayName("플레이-인이 끝나고 스위스 대진이 차면 스위스로 넘어간다")
	@Test
	void switchesToSwissAfterPlayIns() throws Exception {
		JsonNode r = root(stage("Play-Ins", done("p1", "KC", 3, "C9", 0)),
				stage("Swiss", open("s1", "unstarted", "T1", "GEN")), tbdStage("Knockouts", 2));

		assertThat(stageOf(r)).isEqualTo("Swiss");
	}

	@DisplayName("스위스 시작 후: 0-0 도 전적 카드에 담고, 녹아웃에 팀이 없으면 대진은 비운다")
	@Test
	void swissBucketsIncludeUnplayed() throws Exception {
		JsonNode r = root(stage("Play-Ins", done("p1", "KC", 3, "C9", 0)),
				stage("Swiss", done("s1", "T1", 2, "GEN", 0), open("s2", "unstarted", "BLG", "G2")),
				tbdStage("Knockouts", 7));
		Map<String, String[]> info = new LinkedHashMap<>();
		Map<String, TeamMetrics> m = WorldsStandingsBuilder.metrics(WorldsStandingsBuilder.stage(r, "Swiss"), info);

		var bracket = StandingsBracketBuilder.buildWorlds(r, m, info, Map.of()).orElseThrow();

		assertThat(bracket.swiss()).extracting(x -> x.record(), x -> x.status())
				.containsExactly(Tuple.tuple("1-0", "ACTIVE"), Tuple.tuple("0-0", "ACTIVE"), Tuple.tuple("0-1", "ACTIVE"));
		assertThat(bracket.rounds()).isEmpty();
	}

	@DisplayName("3승 진출·3패 탈락·그 사이 진행 순으로 놓는다")
	@Test
	void swissStatusOrder() {
		Map<String, TeamMetrics> m = new LinkedHashMap<>();
		m.put("A", new TeamMetrics(1, 3, 0, 0, 0, 0));
		m.put("B", new TeamMetrics(2, 2, 0, 0, 0, 0));
		m.put("C", new TeamMetrics(3, 1, 0, 0, 0, 0));

		var rows = StandingsBracketBuilder.swissRows(m, Map.of(), 3, 3, true);

		assertThat(rows).extracting(x -> x.status()).containsExactly("ADVANCED", "ACTIVE", "ELIMINATED");
		assertThat(rows).extracting(x -> x.advanced()).containsExactly(true, false, false);
	}

	@DisplayName("서비스: 월즈는 네이버를 부르지 않고 lolesports 로 플레이-인 표를 만든다")
	@Test
	void serviceBuildsWorldsFromLolesports() throws Exception {
		LolesportsStageClient stageClient = mock(LolesportsStageClient.class);
		when(stageClient.standings(any()))
				.thenReturn(Optional.of(root(playIns(), tbdStage("Swiss", 3), tbdStage("Knockouts", 2))));
		StandingsService service = new StandingsService(mock(NaverStandingsClient.class),
				mock(LeagueMatchRepository.class), stageClient);

		StandingsResponse res = service.getStandings("WORLDS");

		assertThat(res.supported()).isTrue();
		assertThat(res.scopeLabel()).isEqualTo("플레이-인");
		assertThat(res.bracket()).isNull();
		assertThat(res.regularFinished()).isFalse();
		assertThat(res.groups().get(0).rows()).extracting(x -> x.teamCode(), x -> x.rank())
				.containsExactly(Tuple.tuple("KC", 1), Tuple.tuple("MVK", 2), Tuple.tuple("FUR", 3), Tuple.tuple("C9", 4));
	}
}
