package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerMemberRow;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerPlayerRow;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.CheerSummary;

/**
 * 백오피스 응원 탭 집계 쿼리를 실제 MySQL 에서 본다 — 정렬 화이트리스트, 기간 필터(NOW - INTERVAL),
 * 선수 필터, 회원별 최다 응원 선수 서브쿼리. H2 에는 이 테이블이 없다(엔티티 없음).
 */
@Testcontainers(disabledWithoutDocker = true)
class PlayerSoloRankCheerBackofficeMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_cheer_test")
			.withUsername("test")
			.withPassword("test")
			.withInitScript("db/pre_v31_schema.sql");

	static PlayerSoloRankCheerRepository repository;

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("30")
				.load()
				.migrate();
		var jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
				MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
		repository = new PlayerSoloRankCheerRepository(jdbc);

		var plain = jdbc.getJdbcTemplate();
		plain.update("INSERT INTO teams (team_id, team_name, team_code) VALUES (1, 'T1', 'T1')");
		plain.update("INSERT INTO players (player_id, player_name, current_team_id) VALUES (1, 'Faker', 1), (2, 'Chovy', NULL)");
		plain.update("INSERT INTO member (id, name, tag, email, role, created_at, quiet_hours_enabled,"
				+ " quiet_start_time, quiet_end_time)"
				+ " VALUES (1, '열혈팬', '0001', 'a@x.com', 'USER', NOW(), 0, '01:00', '08:00'),"
				+ " (2, '라이트팬', '0002', 'b@x.com', 'USER', NOW(), 0, '01:00', '08:00'),"
				+ " (3, '안누름', '0003', 'c@x.com', 'USER', NOW(), 0, '01:00', '08:00')");
		// 회원 1: Faker 두 판(100+50), Chovy 한 판(10). 회원 2: Chovy 한 판(30) — 단 10일 전.
		repository.add(1L, "g1", 1L, 100);
		repository.add(1L, "g2", 1L, 50);
		repository.add(2L, "g3", 1L, 10);
		repository.add(2L, "g3", 2L, 30);
		plain.update("UPDATE player_solo_rank_cheer SET updated_at = NOW(3) - INTERVAL 10 DAY WHERE member_id = 2");
	}

	@Test
	void 요약은_전체와_기간을_나눠_센다() {
		assertThat(repository.summary(null)).isEqualTo(new CheerSummary(190, 2, 2, 3));
		assertThat(repository.summary(7)).isEqualTo(new CheerSummary(160, 1, 2, 3));
	}

	@Test
	void 회원_랭킹은_누른_횟수순이고_최다_응원_선수를_붙인다() {
		var page = repository.members(null, null, null, PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(2);
		CheerMemberRow top = page.getContent().get(0);
		assertThat(top.memberId()).isEqualTo(1L);
		assertThat(top.nickname()).isEqualTo("열혈팬#0001");
		assertThat(top.totalCount()).isEqualTo(160);
		assertThat(top.gameCount()).isEqualTo(3);
		assertThat(top.playerCount()).isEqualTo(2);
		assertThat(top.topPlayerName()).isEqualTo("Faker");
		assertThat(page.getContent().get(1).topPlayerName()).isEqualTo("Chovy");
	}

	@Test
	void 최근순_정렬과_선수_필터와_검색() {
		var recent = repository.members(null, null, null,
				PageRequest.of(0, 20, Sort.by(Sort.Order.desc("lastCheeredAt"))));
		assertThat(recent.getContent()).extracting(CheerMemberRow::memberId).containsExactly(1L, 2L);

		var chovyFans = repository.members(null, 2L, null, PageRequest.of(0, 20));
		assertThat(chovyFans.getContent()).extracting(CheerMemberRow::totalCount).containsExactly(30L, 10L);
		assertThat(chovyFans.getContent().get(1).topPlayerName()).isEqualTo("Chovy");

		assertThat(repository.members(null, null, "b@x", PageRequest.of(0, 20)).getContent())
				.extracting(CheerMemberRow::memberId).containsExactly(2L);
		assertThat(repository.members(7, null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
	}

	@Test
	void 정렬_화이트리스트_밖은_버린다() {
		var page = repository.members(null, null, null,
				PageRequest.of(0, 20, Sort.by("name; DROP TABLE member")));
		assertThat(page.getContent()).hasSize(2);
	}

	@Test
	void 선수_랭킹() {
		var page = repository.players(null, null, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(CheerPlayerRow::playerName).containsExactly("Faker", "Chovy");
		CheerPlayerRow chovy = page.getContent().get(1);
		assertThat(chovy.totalCount()).isEqualTo(40);
		assertThat(chovy.memberCount()).isEqualTo(2);
		assertThat(chovy.gameCount()).isEqualTo(1);
		assertThat(page.getContent().get(0).teamName()).isEqualTo("T1");
	}

	@Test
	void 회원_한_명의_선수별_응원() {
		var page = repository.players(null, 1L, PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(CheerPlayerRow::playerName).containsExactly("Faker", "Chovy");
		assertThat(page.getContent()).extracting(CheerPlayerRow::totalCount).containsExactly(150L, 10L);
		assertThat(page.getContent()).extracting(CheerPlayerRow::gameCount).containsExactly(2L, 1L);
		assertThat(repository.players(null, 3L, PageRequest.of(0, 20)).getContent()).isEmpty();
	}
}
