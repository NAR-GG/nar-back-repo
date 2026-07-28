package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.toy.nar.domain.participant.repository.PlayerRepository.LckPlayerOption;

/**
 * {@link PlayerRepository#findLckPlayerOptions}의 동작을 검증한다.
 *
 * <p>핵심 의미: 선수별로 "가장 최근 LCK 시즌 경기"의 팀 하나로 중복 제거한다.
 * 이적 선수는 최신 팀으로만 1번, 동일 시각 경기가 둘이어도 1번만 나와야 한다.
 *
 * <p>마이그레이션은 MySQL 전용 문법이라 H2에서는 엔티티 기반 스키마(create-drop)를 사용한다.
 * (저장소의 다른 리포지토리 테스트와 동일한 방식.) 엔티티 그래프(Champion/GamePlayerStat)가
 * 무거워 시딩은 네이티브 SQL로 한다.
 */
@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositoryLckPlayerOptionsTest {

	@Autowired
	private PlayerRepository playerRepository;

	@PersistenceContext
	private EntityManager em;

	private static final String LCK = "LCK";
	private static final int YEAR = 2026;
	private static final long T1 = 10L;
	private static final long GEN = 20L;

	@BeforeEach
	void seed() {
		exec("INSERT INTO champions (champion_id, champion_name_kr, champion_name_en, image_url)"
				+ " VALUES (1, '아리', 'Ahri', 'ahri.png')");

		// 리그: LCK 2026(100), LCK 2025(101), LPL 2026(102)
		exec(league(100, "LCK", 2026));
		exec(league(101, "LCK", 2025));
		exec(league(102, "LPL", 2026));

		exec(team(T1, "T1", "T1"));
		exec(team(GEN, "Gen.G", "GEN"));

		exec(player(1, "Faker", "Mid"));
		exec(player(2, "Mover", "Top"));     // 이적: GEN → T1
		exec(player(3, "OldGuy", "Jungle")); // 2025만 → 제외
		exec(player(4, "Other", "Bot"));     // LPL만 → 제외
		exec(player(5, "Twin", "Support"));  // 동일 시각 2경기
		exec(player(6, "GenStar", "Mid"));   // 최신 팀이 GEN

		// games(game_id, league_id, actual_game_start_time)
		exec(game(1, 100, "2026-01-10 10:00:00"));
		exec(game(2, 100, "2026-01-05 10:00:00"));
		exec(game(3, 100, "2026-01-20 10:00:00")); // Mover 최신
		exec(game(4, 101, "2025-06-01 10:00:00")); // 2025
		exec(game(5, 102, "2026-02-01 10:00:00")); // LPL
		exec(game(6, 100, "2026-03-01 00:00:00")); // Twin (동일 시각, 낮은 id) GEN
		exec(game(7, 100, "2026-03-01 00:00:00")); // Twin (동일 시각, 높은 id) T1
		exec(game(8, 100, "2026-01-15 10:00:00")); // GenStar

		// game_participants(id, game_id, player_id, team_id)
		exec(gp(1, 1, 1, T1));   // Faker @T1
		exec(gp(2, 2, 2, GEN));  // Mover @GEN (이전)
		exec(gp(3, 3, 2, T1));   // Mover @T1 (최신) → T1
		exec(gp(4, 4, 3, T1));   // OldGuy 2025
		exec(gp(5, 5, 4, T1));   // Other LPL
		exec(gp(6, 6, 5, GEN));  // Twin @GEN (동일 시각)
		exec(gp(7, 7, 5, T1));   // Twin @T1 (동일 시각, 높은 game_id) → T1
		exec(gp(8, 8, 6, GEN));  // GenStar @GEN

		// 솔랭 전용: LCK 미출전 + KR 주계정 enabled → 목록에 팀 없이 노출돼야 한다.
		exec(player(9, "Deft", "Bot"));
		exec("INSERT INTO player_riot_account"
				+ " (id, player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled,"
				+ " live_status, created_at, updated_at)"
				+ " VALUES (99, 9, 'Deft#8366', 'Deft', '8366', 'KR', 'puuid-deft', true, true,"
				+ " 'OFFLINE', '2026-01-01 00:00:00', '2026-01-01 00:00:00')");

		em.flush();
		em.clear();
	}

	@Test
	@DisplayName("LCK 2026 선수는 최신 경기 팀으로 중복 없이 1번씩, 타 연도/리그는 제외된다")
	void returnsDistinctPlayersWithLatestTeam() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, null, PageRequest.of(0, 50));

		// Faker, Mover, Twin, GenStar, Deft = 5명 (OldGuy=2025, Other=LPL 제외)
		// Deft는 계정 보유 비출전 선수로 팀 없이 포함된다.
		assertThat(page.getTotalElements()).isEqualTo(5);
		assertThat(page.getContent()).extracting(LckPlayerOption::getPlayerName)
				.containsExactlyInAnyOrder("Faker", "Mover", "Twin", "GenStar", "Deft")
				.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("이적 선수는 가장 최근 경기의 팀(T1)으로 표시된다")
	void transferredPlayerShowsLatestTeam() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, "Mover", PageRequest.of(0, 50));

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).getTeamId()).isEqualTo(T1);
	}

	@Test
	@DisplayName("동일 시각 경기가 둘이어도 선수는 1번만, 타이브레이크로 한 팀만 나온다")
	void sameTimestampGamesDoNotDuplicate() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, "Twin", PageRequest.of(0, 50));

		// 구버전(= MAX + DISTINCT)은 두 팀 행을 모두 반환해 2건이 됨 → 1건이어야 한다.
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).getTeamId()).isEqualTo(T1); // game_id 높은 쪽
	}

	@Test
	@DisplayName("teamId 필터는 최신 팀이 해당 팀인 선수만 반환한다")
	void filtersByLatestTeam() {
		Page<LckPlayerOption> genTeam = playerRepository.findLckPlayerOptions(
				LCK, YEAR, GEN, null, PageRequest.of(0, 50));
		assertThat(genTeam.getContent()).extracting(LckPlayerOption::getPlayerName)
				.containsExactly("GenStar");

		Page<LckPlayerOption> t1Team = playerRepository.findLckPlayerOptions(
				LCK, YEAR, T1, null, PageRequest.of(0, 50));
		assertThat(t1Team.getContent()).extracting(LckPlayerOption::getPlayerName)
				.containsExactlyInAnyOrder("Faker", "Mover", "Twin");
	}

	@Test
	@DisplayName("솔랭 전용 선수(계정 보유, LCK 미출전)도 팀 없이 목록에 포함된다")
	void includesSoloRankOnlyPlayer() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, null, PageRequest.of(0, 50));

		LckPlayerOption deft = page.getContent().stream()
				.filter(o -> "Deft".equals(o.getPlayerName()))
				.findFirst()
				.orElseThrow();
		assertThat(deft.getTeamId()).isNull();
		assertThat(deft.getTeamName()).isNull();
	}

	@Test
	@DisplayName("해외 플랫폼(NA1) 주계정 선수도 목록에 포함된다")
	void includesNonKrPlatformSoloRankPlayer() {
		// 해외 리그로 이적한 선수는 계정이 NA/EUW로 저장된다. 예전엔 platform='KR' 조건 때문에
		// 라이브 감지는 되는데 구독 목록에는 안 뜨는 모순이 있었다.
		exec(player(10, "Quad", "Mid"));
		exec("INSERT INTO player_riot_account"
				+ " (id, player_id, riot_id, game_name, tag_line, platform, puuid, primary_account, enabled,"
				+ " live_status, created_at, updated_at)"
				+ " VALUES (100, 10, 'FLY Quad#123', 'FLY Quad', '123', 'NA1', 'puuid-quad', true, true,"
				+ " 'OFFLINE', '2026-01-01 00:00:00', '2026-01-01 00:00:00')");
		em.flush();
		em.clear();

		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, null, PageRequest.of(0, 50));

		assertThat(page.getContent()).extracting(LckPlayerOption::getPlayerName).contains("Quad");
		assertThat(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(10L)))
				.extracting(LckPlayerOption::getPlayerName).containsExactly("Quad");
	}

	@Test
	@DisplayName("teamId 필터가 있으면 팀 없는 솔랭 전용 선수는 제외된다")
	void excludesSoloRankOnlyPlayerWhenTeamFilterSet() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, T1, null, PageRequest.of(0, 50));

		assertThat(page.getContent()).extracting(LckPlayerOption::getPlayerName)
				.doesNotContain("Deft");
	}

	@Test
	@DisplayName("이름 검색은 부분 일치(대소문자 무시)로 동작한다")
	void filtersByNameQuery() {
		Page<LckPlayerOption> page = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, "fak", PageRequest.of(0, 50));
		assertThat(page.getContent()).extracting(LckPlayerOption::getPlayerName)
				.containsExactly("Faker");
	}

	@Test
	@DisplayName("페이지네이션: size=2, page0 은 포지션순 2건 + 전체 5건/3페이지")
	void paginates() {
		Page<LckPlayerOption> page0 = playerRepository.findLckPlayerOptions(
				LCK, YEAR, null, null, PageRequest.of(0, 2));

		// Deft(Bot→ELSE=6) 추가로 총 5명. 포지션순: Mover(Top), Faker(Mid), GenStar(Mid), Twin(Support), Deft(Bot)
		assertThat(page0.getTotalElements()).isEqualTo(5);
		assertThat(page0.getTotalPages()).isEqualTo(3);
		// page0: Mover(Top=1), Faker(Mid=3)
		assertThat(page0.getContent()).extracting(LckPlayerOption::getPlayerName)
				.containsExactly("Mover", "Faker");
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

	/**
	 * 컨텍스트를 game·participant 도메인으로 한정한다. (전체 앱을 띄우면 Elasticsearch 빈을
	 * 요구해 슬라이스 테스트가 실패하므로, 저장소의 다른 리포지토리 테스트와 동일하게 범위를 좁힌다.)
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackages = {
			"com.toy.nar.domain.game.entity",
			"com.toy.nar.domain.participant.entity"
	})
	@EnableJpaRepositories(basePackageClasses = PlayerRepository.class)
	static class TestJpaConfiguration {
	}
}
