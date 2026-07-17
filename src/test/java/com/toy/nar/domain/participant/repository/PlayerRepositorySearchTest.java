package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.participant.entity.Player;

// 백오피스 선수 검색의 리그 필터 검증. 기준 = 출전 기록(GameParticipant→Game→League).
// league_teams는 오염돼 있어(LCK에 462팀) 판정 기준으로 쓰지 않는다 — 회귀 방지용 테스트.
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositorySearchTest {

	@Autowired
	private PlayerRepository playerRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void seed() {
		exec("INSERT INTO champions (champion_id, champion_name_kr, champion_name_en, image_url)"
				+ " VALUES (1, '아리', 'Ahri', 'ahri.png')");
		exec(league(100, "LCK", 2026));
		exec(league(101, "LPL", 2026));
		exec(team(10, "T1", "T1"));
		exec(team(11, "Weibo Gaming", "WBG"));

		exec(player(1, "Chovy", "Mid"));   // LCK 출전
		exec(player(2, "Xiaohu", "Mid"));  // LPL만 출전
		exec(player(3, "Rookie", "Mid"));  // 출전 기록 없음

		exec(game(1, 100, "2026-01-10 10:00:00")); // LCK
		exec(game(2, 101, "2026-01-15 10:00:00")); // LPL
		exec(gp(1, 1, 1, 10)); // Chovy @LCK
		exec(gp(2, 2, 2, 11)); // Xiaohu @LPL

		em.flush();
		em.clear();
	}

	@Test
	@DisplayName("league를 주면 그 리그 출전 기록이 있는 선수만 나온다")
	void searchForBackoffice_filtersByParticipationLeague() {
		List<String> lckPlayers = playerRepository.searchForBackoffice(null, "LCK", PageRequest.of(0, 10))
				.map(Player::getName).getContent();
		assertThat(lckPlayers).containsExactly("Chovy");

		// league 없으면 전체(출전 기록 없는 선수 포함)
		assertThat(playerRepository.searchForBackoffice(null, null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// q 결합
		List<String> lckCho = playerRepository.searchForBackoffice("cho", "LCK", PageRequest.of(0, 10))
				.map(Player::getName).getContent();
		assertThat(lckCho).containsExactly("Chovy");

		// LPL만 뛴 선수는 LCK 필터에 안 걸린다
		List<String> lpl = playerRepository.searchForBackoffice(null, "LPL", PageRequest.of(0, 10))
				.map(Player::getName).getContent();
		assertThat(lpl).containsExactly("Xiaohu");
	}

	// ── 시딩 헬퍼 (PlayerRepositoryLckParticipationTest와 동일 패턴) ──

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
