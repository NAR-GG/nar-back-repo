package com.toy.nar.app.lolesports.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * 순위 집계용 쿼리가 실제로 파싱·실행되는지 본다.
 *
 * <p>단위 테스트는 계산만 검증하므로 JPQL 오타나 IN 파라미터 바인딩 문제를 못 잡는다.
 * H2 로 띄워서 쿼리 자체를 태운다.
 */
@DataJpaTest(properties = {
		// Flyway 마이그레이션은 MySQL 전용이라 H2 에서 V1__init.sql 부터 깨진다.
		// 이 테스트가 보려는 건 스키마가 아니라 쿼리라서, 엔티티로 스키마를 만들고 간다.
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
class LeagueMatchStandingsQueryTest {

	@Autowired
	private LeagueMatchRepository repository;

	private static LeagueMatch match(String id, String league, Integer year, String split,
			String title, String date) {
		return LeagueMatch.builder()
				.id(id).leagueName(league).seasonYear(year).seasonSplit(split)
				.matchTitle(title).matchDate(LocalDateTime.parse(date)).state("completed")
				.blueTeamCode("GEN").blueScore(2).redTeamCode("KT").redScore(1).bestOf(3)
				.build();
	}

	@BeforeEach
	void setUp() {
		repository.saveAll(List.of(
				match("s1", "LCK", 2026, "Split 1", "1주 차 | GEN vs KT", "2026-01-14T08:00"),
				match("s2", "LCK", 2026, "Split 2", "9주 차 | GEN vs KT", "2026-05-27T08:00"),
				match("s3", "LCK", 2026, "Split 3", "13주 차 | GEN vs KT", "2026-08-19T08:00"),
				match("s4", "LCK", 2026, "Split 3", "플레이오프 | GEN vs KT", "2026-08-29T08:00"),
				match("s5", "LPL", 2026, "Split 3", "5주 차 | BLG vs TES", "2026-08-19T08:00")));
	}

	@DisplayName("리그·시즌·스플릿으로 거른다 — 다른 리그와 Split 1 은 안 들어온다")
	@Test
	void filtersByLeagueSeasonAndSplits() {
		List<LeagueMatch> rows = repository.findForStandings("LCK", 2026, List.of("Split 2", "Split 3"));

		assertThat(rows).extracting(LeagueMatch::getId).containsExactly("s2", "s3", "s4");
	}

	@DisplayName("스플릿 목록이 하나여도 IN 바인딩이 동작한다")
	@Test
	void singleSplitBinding() {
		List<LeagueMatch> rows = repository.findForStandings("LCK", 2026, List.of("Split 3"));

		assertThat(rows).extracting(LeagueMatch::getId).containsExactly("s3", "s4");
	}

	@DisplayName("정렬은 경기 날짜 오름차순이다 — 연속 계산이 여기 의존한다")
	@Test
	void orderedByMatchDate() {
		List<LeagueMatch> rows = repository.findForStandings("LCK", 2026, List.of("Split 2", "Split 3"));

		assertThat(rows).extracting(LeagueMatch::getMatchDate).isSorted();
	}

	@DisplayName("리그의 최신 경기로 현재 시즌을 알아낸다")
	@Test
	void latestMatchGivesCurrentSeason() {
		LeagueMatch latest = repository.findTopByLeagueNameOrderByMatchDateDesc("LCK");

		assertThat(latest.getId()).isEqualTo("s4");
		assertThat(latest.getSeasonYear()).isEqualTo(2026);
	}
}
