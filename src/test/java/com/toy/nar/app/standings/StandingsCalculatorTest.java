package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;

class StandingsCalculatorTest {

	private static LeagueMatch match(String date, String blue, int blueScore, int redScore, String red, String state) {
		return LeagueMatch.builder()
				.id(date + blue + red)
				.leagueName("LCK")
				.matchTitle("13주 차 | " + blue + " vs " + red)
				.matchDate(LocalDateTime.parse(date))
				.state(state)
				.blueTeamCode(blue)
				.blueScore(blueScore)
				.redTeamCode(red)
				.redScore(redScore)
				.bestOf(3)
				.build();
	}

	@DisplayName("세트 승패를 합산하고 매치 승패를 센다")
	@Test
	void aggregatesSetsAndRecord() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed"),
				match("2026-08-20T08:00", "GEN", 0, 2, "T1", "completed")));

		assertThat(m.get("GEN").wins()).isEqualTo(1);
		assertThat(m.get("GEN").losses()).isEqualTo(1);
		assertThat(m.get("GEN").setWins()).isEqualTo(2);   // 2 + 0
		assertThat(m.get("GEN").setLosses()).isEqualTo(3); // 1 + 2
		assertThat(m.get("GEN").setDiff()).isEqualTo(-1);
		assertThat(m.get("KT").setWins()).isEqualTo(1);
		assertThat(m.get("KT").setLosses()).isEqualTo(2);
	}

	@DisplayName("연속 기록은 승이면 양수 패면 음수, 끊기면 리셋된다")
	@Test
	void streakSignAndReset() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-01T08:00", "T1", 2, 0, "KT", "completed"),
				match("2026-08-02T08:00", "T1", 2, 1, "DK", "completed"),
				match("2026-08-03T08:00", "T1", 0, 2, "GEN", "completed"),
				match("2026-08-04T08:00", "T1", 1, 2, "HLE", "completed")));

		assertThat(m.get("T1").streak()).isEqualTo(-2); // 2연승 후 2연패
		assertThat(m.get("KT").streak()).isEqualTo(-1);
		assertThat(m.get("GEN").streak()).isEqualTo(1);
	}

	@DisplayName("입력 순서가 뒤섞여도 경기 날짜순으로 연속을 센다")
	@Test
	void streakUsesMatchDateNotInputOrder() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-03T08:00", "T1", 0, 2, "GEN", "completed"),
				match("2026-08-01T08:00", "T1", 2, 0, "KT", "completed")));

		assertThat(m.get("T1").streak()).isEqualTo(-1); // 최근 경기가 패
	}

	@DisplayName("안 끝난 경기는 잔여로 세고 전적에는 넣지 않는다")
	@Test
	void unfinishedCountsAsRemaining() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed"),
				match("2026-08-22T08:00", "DK", 0, 0, "GEN", "unstarted")));

		assertThat(m.get("GEN").wins()).isEqualTo(1);
		assertThat(m.get("GEN").remaining()).isEqualTo(1);
		assertThat(m.get("DK").wins()).isZero();
		assertThat(m.get("DK").remaining()).isEqualTo(1);
	}

	@DisplayName("state 가 아직 안 옮겨졌어도 스코어로 승부가 갈렸으면 집계한다")
	@Test
	void scoreDecidesWhenStateLags() {
		// 라이브 경로가 스코어를 먼저 넣고 state 는 나중에 옮긴다. 그 사이 경기가
		// 순위에서 통째로 빠지면 안 된다.
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-21T08:00", "BFX", 2, 1, "BRO", "inProgress")));

		assertThat(m.get("BFX").wins()).isEqualTo(1);
		assertThat(m.get("BFX").remaining()).isZero();
		assertThat(m.get("BRO").losses()).isEqualTo(1);
	}

	@DisplayName("Bo3 에서 1-0 은 아직 진행 중이라 잔여로 남는다")
	@Test
	void leadingButNotClinchedIsRemaining() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-21T08:00", "BFX", 1, 0, "BRO", "inProgress")));

		assertThat(m.get("BFX").wins()).isZero();
		assertThat(m.get("BFX").remaining()).isEqualTo(1);
	}

	@DisplayName("브래킷 빈 슬롯(TBD)은 어느 팀의 잔여도 아니다")
	@Test
	void tbdSlotsAreIgnored() {
		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(
				match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed"),
				match("2026-08-29T08:00", "TBD", 0, 0, "TBD", "unstarted")));

		assertThat(m).containsOnlyKeys("GEN", "KT");
		assertThat(m.get("GEN").remaining()).isZero();
	}

	@DisplayName("Bo5 는 3세트를 이겨야 확정이다")
	@Test
	void bestOfFiveNeedsThreeSets() {
		LeagueMatch bo5 = LeagueMatch.builder()
				.id("f1").leagueName("LCK").matchTitle("13주 차 | T1 vs GEN")
				.matchDate(LocalDateTime.parse("2026-09-13T08:00")).state("inProgress")
				.blueTeamCode("T1").blueScore(2).redTeamCode("GEN").redScore(1)
				.bestOf(5).build();

		Map<String, TeamMetrics> m = StandingsCalculator.compute(List.of(bo5));

		assertThat(m.get("T1").wins()).isZero();
		assertThat(m.get("T1").remaining()).isEqualTo(1);
	}
}
