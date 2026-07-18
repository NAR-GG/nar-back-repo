package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.participant.entity.Team;

// 백오피스 검색 쿼리(searchForBackoffice) 검증. H2 격리 실행(마이그레이션은 MySQL 전용이라 엔티티 기반 스키마 사용).
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class TeamRepositorySearchTest {

	@Autowired
	private TeamRepository teamRepository;

	@PersistenceContext
	private EntityManager em;

	@Test
	@DisplayName("q가 null이면 전체, 값이 있으면 팀명·코드 부분일치(대소문자 무시)로 검색한다")
	void searchForBackoffice_matchesNameOrCode() {
		teamRepository.save(Team.builder().name("Gen.G").code("GEN").build());
		teamRepository.save(Team.builder().name("Dplus KIA").code("DK").build());
		teamRepository.save(Team.builder().name("T1").code("T1").build());

		// null → 전체
		assertThat(teamRepository.searchForBackoffice(null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// 팀명 부분일치(소문자 입력도 매칭)
		List<String> byName = teamRepository.searchForBackoffice("gen", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byName).containsExactly("Gen.G");

		// 코드 부분일치
		List<String> byCode = teamRepository.searchForBackoffice("dk", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byCode).containsExactly("Dplus KIA");

		// 매칭 없음
		Page<Team> none = teamRepository.searchForBackoffice("zzz", PageRequest.of(0, 10));
		assertThat(none.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("league를 주면 그 리그 출전 기록이 있는 팀만, q와 함께도 동작한다")
	void searchForBackoffice_filtersByLeague() {
		// 출전 기록(GameParticipant) 기준 — league_teams는 오염돼 있어 판정에 쓰지 않는다.
		exec("INSERT INTO champions (champion_id, champion_name_kr, champion_name_en, image_url)"
				+ " VALUES (1, '아리', 'Ahri', 'ahri.png')");
		exec("INSERT INTO leagues (league_id, league_name, season_year, season_split, is_playoffs)"
				+ " VALUES (100, 'LCK', 2025, 'Spring', false)");
		exec("INSERT INTO leagues (league_id, league_name, season_year, season_split, is_playoffs)"
				+ " VALUES (101, 'LPL', 2025, 'Spring', false)");
		exec("INSERT INTO teams (team_id, team_name, team_code, team_image_url) VALUES (10, 'Gen.G', 'GEN', 'g.png')");
		exec("INSERT INTO teams (team_id, team_name, team_code, team_image_url) VALUES (11, 'T1', 'T1', 't.png')");
		exec("INSERT INTO teams (team_id, team_name, team_code, team_image_url) VALUES (12, 'Weibo Gaming', 'WBG', 'w.png')");
		exec("INSERT INTO players (player_id, player_name, image_url, role) VALUES (1, 'p1', 'p1.png', 'Mid')");
		exec("INSERT INTO games (game_id, league_id, actual_game_start_time, game_number, patch,"
				+ " game_length_seconds, ckpm) VALUES (1, 100, '2025-01-10 10:00:00', 1, '14.1', 1800, 0.5)");
		exec("INSERT INTO games (game_id, league_id, actual_game_start_time, game_number, patch,"
				+ " game_length_seconds, ckpm) VALUES (2, 101, '2025-01-15 10:00:00', 1, '14.1', 1800, 0.5)");
		// LCK 경기: Gen.G, T1 출전 / LPL 경기: Weibo 출전
		exec("INSERT INTO game_participants (participant_game_id, game_id, player_id, team_id, side, position,"
				+ " champion_id, is_win) VALUES (1, 1, 1, 10, 'Blue', 'mid', 1, true)");
		exec("INSERT INTO game_participants (participant_game_id, game_id, player_id, team_id, side, position,"
				+ " champion_id, is_win) VALUES (2, 1, 1, 11, 'Red', 'mid', 1, false)");
		exec("INSERT INTO game_participants (participant_game_id, game_id, player_id, team_id, side, position,"
				+ " champion_id, is_win) VALUES (3, 2, 1, 12, 'Blue', 'mid', 1, true)");
		em.flush();
		em.clear();

		// LCK 출전 팀만
		List<String> lckTeams = teamRepository.searchForBackofficeInLeague(null, "LCK", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(lckTeams).containsExactlyInAnyOrder("Gen.G", "T1");

		// LCK + q 결합
		List<String> lckGen = teamRepository.searchForBackofficeInLeague("gen", "LCK", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(lckGen).containsExactly("Gen.G");

		// 출전 팀 없는 리그
		assertThat(teamRepository.searchForBackofficeInLeague(null, "LEC", PageRequest.of(0, 10)).getTotalElements())
				.isZero();
	}

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}
}
