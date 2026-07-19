package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import com.toy.nar.domain.participant.entity.Player;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositorySoloRankSyncTargetsTest {

	@Autowired
	private PlayerRepository playerRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void seed() {
		exec("INSERT INTO leagues (league_id, league_name, season_year, season_split, is_playoffs)"
				+ " VALUES (100, 'LCK', 2026, 'Spring', false)");
		exec("INSERT INTO teams (team_id, team_name, team_code, team_image_url)"
				+ " VALUES (10, 'T1', 'T1', 'T1.png')");
		// 1: LCK 출전자, 2: 계정만 보유(비출전), 3: 무관(제외)
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (1, 'Faker', false, false)");
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (2, 'Deft', false, true)");
		exec("INSERT INTO players (player_id, player_name, image_locked, game_accounts_locked)"
				+ " VALUES (3, 'NoOne', false, false)");
		exec("INSERT INTO champions (champion_id, champion_name_kr, champion_name_en, image_url)"
				+ " VALUES (1, '아리', 'Ahri', 'ahri.png')");
		exec("INSERT INTO games (game_id, league_id, actual_game_start_time, game_number, patch,"
				+ " game_length_seconds, ckpm)"
				+ " VALUES (1, 100, '2026-01-10 10:00:00', 1, '14.1', 1800, 0.5)");
		exec("INSERT INTO game_participants (participant_game_id, game_id, player_id, team_id,"
				+ " side, position, champion_id, is_win)"
				+ " VALUES (1, 1, 1, 10, 'Blue', 'mid', 1, true)");
		exec("INSERT INTO player_riot_account"
				+ " (id, player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled,"
				+ " live_status, created_at, updated_at)"
				+ " VALUES (1, 2, 'Deft#8366', 'Deft', '8366', 'KR', 'puuid-deft', true, true,"
				+ " 'OFFLINE', NOW(), NOW())");
	}

	@Test
	void includesLeaguePlayersAndAccountHolders() {
		List<Player> targets = playerRepository.findSoloRankSyncTargets("LCK");
		assertThat(targets).extracting(Player::getName)
				.containsExactlyInAnyOrder("Faker", "Deft");
	}

	private void exec(String sql) {
		em.createNativeQuery(sql).executeUpdate();
	}
}
