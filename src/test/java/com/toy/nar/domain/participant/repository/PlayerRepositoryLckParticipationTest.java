package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * {@link PlayerRepository#hasLeagueParticipation}의 동작을 검증한다.
 *
 * <p>LCK 경기 출전 기록이 있으면 true, 다른 리그(LPL)만 출전한 선수는 false를 반환한다.
 *
 * <p>마이그레이션은 MySQL 전용 문법이라 H2에서는 엔티티 기반 스키마(create-drop)를 사용한다.
 * 기존 PlayerRepositoryLckPlayerOptionsTest의 @SpringBootConfiguration 이너 클래스를 재사용해
 * 동일 패키지에서 설정 충돌(multiple @SpringBootConfiguration)이 발생하지 않도록 한다.
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositoryLckParticipationTest {

	@Autowired
	private PlayerRepository playerRepository;

	@PersistenceContext
	private EntityManager em;

	// LCK 출전 기록이 있는 선수
	private Long fakerId;
	// LPL 경기만 출전한 선수
	private Long lplOnlyId;

	@BeforeEach
	void seed() {
		exec("INSERT INTO champions (champion_id, champion_name_kr, champion_name_en, image_url)"
				+ " VALUES (1, '아리', 'Ahri', 'ahri.png')");

		// 리그: LCK(100), LPL(101)
		exec(league(100, "LCK", 2026));
		exec(league(101, "LPL", 2026));

		exec(team(10, "T1", "T1"));

		// faker: LCK 출전, lplOnly: LPL만 출전
		exec(player(1, "faker", "Mid"));
		exec(player(2, "lplOnly", "Bot"));

		// 경기
		exec(game(1, 100, "2026-01-10 10:00:00")); // LCK
		exec(game(2, 101, "2026-01-15 10:00:00")); // LPL

		// 참가 기록
		exec(gp(1, 1, 1, 10)); // faker @LCK
		exec(gp(2, 2, 2, 10)); // lplOnly @LPL

		em.flush();
		em.clear();

		fakerId = 1L;
		lplOnlyId = 2L;
	}

	@Test
	@DisplayName("LCK 경기 출전 기록이 있으면 true, 없으면 false")
	void hasLeagueParticipation() {
		assertThat(playerRepository.hasLeagueParticipation(fakerId, "LCK")).isTrue();
		assertThat(playerRepository.hasLeagueParticipation(lplOnlyId, "LCK")).isFalse();
	}

	@Test
	@DisplayName("findWithCurrentTeamById는 currentTeam을 즉시 로딩한다(OSIV off 직렬화 대비)")
	void findWithCurrentTeamById_initializesCurrentTeam() {
		// 픽스처: faker 선수에 currentTeam(T1, id=10) 세팅
		exec("UPDATE players SET current_team_id = 10 WHERE player_id = 1");
		em.flush();
		em.clear();

		// EntityGraph 조회
		com.toy.nar.domain.participant.entity.Player found =
				playerRepository.findWithCurrentTeamById(fakerId).orElseThrow();

		// 세션 경계 밖에서도 currentTeam이 초기화돼 있어야 한다(OSIV off 대비)
		assertThat(Hibernate.isInitialized(found.getCurrentTeam())).isTrue();
		assertThat(found.getCurrentTeam().getName()).isEqualTo("T1");
	}

	// ── 시딩 헬퍼 (네이티브 SQL) ─────────────────────────────────

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}

	private static String league(long id, String name, int year) {
		return "INSERT INTO leagues (league_id, league_name, season_year, season_split, is_playoffs)"
				+ " VALUES (" + id + ", '" + name + "', " + year + ", 'Spring', false)";
	}

	private static String team(long id, String name, String code) {
		return "INSERT INTO teams (team_id, team_name, team_code, team_image_url)"
				+ " VALUES (" + id + ", '" + name + "', '" + code + "', '" + code + ".png')";
	}

	private static String player(long id, String name, String role) {
		return "INSERT INTO players (player_id, player_name, image_url, role)"
				+ " VALUES (" + id + ", '" + name + "', '" + name + ".png', '" + role + "')";
	}

	private static String game(long id, long leagueId, String startTime) {
		return "INSERT INTO games (game_id, league_id, actual_game_start_time, game_number, patch,"
				+ " game_length_seconds, ckpm) VALUES (" + id + ", " + leagueId + ", '" + startTime
				+ "', 1, '14.1', 1800, 0.5)";
	}

	private static String gp(long id, long gameId, long playerId, long teamId) {
		return "INSERT INTO game_participants (participant_game_id, game_id, player_id, team_id,"
				+ " side, position, champion_id, is_win) VALUES (" + id + ", " + gameId + ", "
				+ playerId + ", " + teamId + ", 'Blue', 'mid', 1, true)";
	}
}
