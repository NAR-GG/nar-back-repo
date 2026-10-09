package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;
import com.toy.nar.app.standings.dto.StandingsResponse.Bracket;

class StandingsBracketBuilderTest {

	private static final String TBD = "{\"code\":\"TBD\",\"result\":null}";

	private static JsonNode lol(String swissState, String round5State, String po) throws Exception {
		return new ObjectMapper().readTree("""
				{"data":{"standings":[{"stages":[
				 {"name":"Swiss","sections":[{"matches":[{"id":"s1","state":"%s","teams":[]}]}]},
				 {"name":"Round 5","sections":[{"matches":[{"id":"r5","state":"%s","teams":[]}]}]},
				 {"name":"Playoffs","sections":[{"matches":[%s]}]}]}]}}
				""".formatted(swissState, round5State, po));
	}

	private static String poMatches(String firstTeamA) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 7; i++) {
			String a = i == 1 ? firstTeamA : TBD;
			sb.append(i > 1 ? "," : "").append("{\"id\":\"p").append(i).append("\",\"state\":\"unstarted\",\"teams\":[")
					.append(a).append(",").append(TBD).append("]}");
		}
		return sb.toString();
	}

	@DisplayName("8강에 팀이 한 곳도 없으면 대진을 내리지 않는다 — 앱이 계속 순위표를 그린다")
	@Test
	void noBracketWhileAllTbd() throws Exception {
		assertThat(StandingsBracketBuilder.build(lol("completed", "unstarted", poMatches(TBD)), Map.of(), Map.of(), Map.of()))
				.isEmpty();
	}

	@DisplayName("7경기는 8강 4·4강 2·결승 1 로 나누고 미정 팀은 teamCode null, 결승만 isFinal")
	@Test
	void splitsSevenMatchesIntoRounds() throws Exception {
		String kt = "{\"code\":\"KT\",\"name\":\"kt\",\"image\":\"i\",\"result\":{\"outcome\":\"win\",\"gameWins\":3}}";
		Map<String, OffsetDateTime> times = Map.of("p1", OffsetDateTime.parse("2026-10-12T03:00:00Z"));
		Bracket b = StandingsBracketBuilder.build(lol("completed", "completed", poMatches(kt)), Map.of(), Map.of(), times)
				.orElseThrow();

		assertThat(b.rounds()).extracting(r -> r.name()).containsExactly("8강", "4강", "결승");
		assertThat(b.rounds()).extracting(r -> r.matches().size()).containsExactly(4, 2, 1);
		var first = b.rounds().get(0).matches().get(0);
		assertThat(first.teamA().teamCode()).isEqualTo("KT");
		assertThat(first.teamA().won()).isTrue();
		assertThat(first.teamA().gameWins()).isEqualTo(3);
		assertThat(first.teamB().teamCode()).isNull();
		assertThat(first.scheduledTime()).isEqualTo(OffsetDateTime.parse("2026-10-12T03:00:00Z"));
		assertThat(first.status()).isEqualTo("upcoming");
		assertThat(first.isFinal()).isFalse();
		assertThat(b.rounds().get(2).matches().get(0).isFinal()).isTrue();
	}

	@DisplayName("스위스 전적 버킷은 승 많은 순으로 묶고 2승부터 진출")
	@Test
	void swissBucketsGroupByRecord() {
		Map<String, TeamMetrics> m = new LinkedHashMap<>();
		m.put("NAVI", new TeamMetrics(1, 2, 0, 0, 0, 0));
		m.put("KT", new TeamMetrics(2, 0, 0, 0, 0, 0));
		m.put("BFX", new TeamMetrics(2, 0, 0, 0, 0, 0));
		m.put("FLY", new TeamMetrics(0, 2, 0, 0, 0, 0));

		var rows = StandingsBracketBuilder.swissRows(m, Map.of("KT", new String[] { "kt Rolster", "http://kt.png" }));

		assertThat(rows).extracting(r -> r.record()).containsExactly("2-0", "1-2", "0-2");
		assertThat(rows.get(0).teamCodes()).containsExactly("BFX", "KT");
		// 정보 있는 팀은 이름·로고, 없는 팀은 코드로 폴백
		assertThat(rows.get(0).teams()).extracting(t -> t.teamName(), t -> t.imageUrl())
				.containsExactly(org.assertj.core.groups.Tuple.tuple("BFX", null),
						org.assertj.core.groups.Tuple.tuple("kt Rolster", "http://kt.png"));
		assertThat(rows).extracting(r -> r.advanced()).containsExactly(true, false, false);
	}

	@DisplayName("Playoffs 를 뺀 스테이지가 전부 끝나야 그룹 스테이지 종료")
	@Test
	void groupStagesFinishedNeedsEveryStage() throws Exception {
		assertThat(StandingsBracketBuilder.groupStagesFinished(lol("completed", "unstarted", poMatches(TBD)))).isFalse();
		assertThat(StandingsBracketBuilder.groupStagesFinished(lol("completed", "completed", poMatches(TBD)))).isTrue();
	}
}
