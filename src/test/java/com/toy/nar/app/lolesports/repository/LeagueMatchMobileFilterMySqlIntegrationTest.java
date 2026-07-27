package com.toy.nar.app.lolesports.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모바일 일정 필터 쿼리의 복수 리그·팀(List IN) 및 ALL(null=조건 제거) 동작을 실제 MySQL 8 + Hibernate 6.6 에서 검증한다.
 * null 컬렉션 파라미터 관용구({@code :leagueNames IS NULL OR m.leagueName IN :leagueNames})가 런타임에 깨지지 않음을 보장한다.
 * 로컬 dev MySQL(docker-compose, 3308)의 격리 스키마 nar_mobile_filter_test 에 대해 ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회): CREATE DATABASE nar_mobile_filter_test; GRANT ALL ON nar_mobile_filter_test.* TO 'nar_id'@'%';
 * 실행: ./gradlew test -Ddataintegrity.local.enabled=true --tests "...LeagueMatchMobileFilterMySqlIntegrationTest"
 * (dataintegrity. prefix 는 build.gradle 이 테스트 JVM 으로 포워딩하는 로컬 통합 테스트용 플래그다.)
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeagueMatchMobileFilterMySqlIntegrationTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://localhost:3308/nar_mobile_filter_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
		registry.add("spring.datasource.username", () -> "nar_id");
		registry.add("spring.datasource.password", () -> "nar_pw");
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private LeagueMatchRepository repository;

	private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
	private static final LocalDateTime END = LocalDateTime.of(2026, 8, 1, 0, 0);

	@BeforeEach
	void seed() {
		repository.saveAll(List.of(
				match("m-lck", "LCK", LocalDateTime.of(2026, 7, 10, 9, 0), "T1", "GEN"),
				match("m-lec", "LEC", LocalDateTime.of(2026, 7, 11, 9, 0), "G2", "FNC"),
				match("m-kespa", "KESPA", LocalDateTime.of(2026, 7, 12, 9, 0), "T1", "KT")));
	}

	@Test
	void nullLeagueNamesReturnsAllLeagues() {
		List<LeagueMatch> result = repository.findMobileMatchesInRange(null, START, END);

		assertThat(result).extracting(LeagueMatch::getId)
				.containsExactlyInAnyOrder("m-lck", "m-lec", "m-kespa");
	}

	@Test
	void leagueNamesListFiltersByIn() {
		List<LeagueMatch> result = repository.findMobileMatchesInRange(List.of("LCK", "KESPA"), START, END);

		assertThat(result).extracting(LeagueMatch::getId)
				.containsExactlyInAnyOrder("m-lck", "m-kespa");
	}

	@Test
	void teamNamesListMatchesEitherSide() {
		// 팀 이름은 소문자로 정규화해 넘긴다(서비스와 동일). T1 은 m-lck(blue)·m-kespa(blue) 두 경기 출전.
		List<LeagueMatch> result = repository.findMobileTeamMatchesInRange(
				null, List.of("t1", "fnc"), null, START, END);

		assertThat(result).extracting(LeagueMatch::getId)
				.containsExactlyInAnyOrder("m-lck", "m-lec", "m-kespa");
	}

	@Test
	void teamCodesListMatchesWhenProvided() {
		List<LeagueMatch> result = repository.findMobileTeamMatchesInRange(
				List.of("LCK"), List.of("nomatch"), List.of("gen"), START, END);

		assertThat(result).extracting(LeagueMatch::getId)
				.containsExactly("m-lck");
	}

	private LeagueMatch match(String id, String league, LocalDateTime date, String blue, String red) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchTitle(blue + " vs " + red)
				.matchDate(date)
				.state("unstarted")
				.blueTeamName(blue)
				.blueTeamCode(blue)
				.redTeamName(red)
				.redTeamCode(red)
				.build();
	}
}
