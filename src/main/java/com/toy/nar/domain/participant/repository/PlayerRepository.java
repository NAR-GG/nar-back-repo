package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PlayerRepository extends JpaRepository<Player, Long> {
	Optional<Player> findByName(String name);

	// 백오피스 검색: 선수명·실명 부분일치 + 리그 필터. q/league 가 null 이면 각 조건 무시.
	// 리그는 출전 기록(GameParticipant→Game→League) 기준 EXISTS 로 판정(전 시즌 통합).
	@Query("""
			SELECT p FROM Player p
			WHERE (:q IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(p.realName) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND (:league IS NULL
			       OR EXISTS (SELECT 1 FROM GameParticipant gp
			                  WHERE gp.player = p AND gp.game.league.leagueName = :league))
			""")
	Page<Player> searchForBackoffice(@Param("q") String q, @Param("league") String league, Pageable pageable);

	Optional<Player> findByPlayerOriginId(String playerOriginId);

	List<Player> findAllByNameInIgnoreCase(Set<String> names);

	@Query("SELECT DISTINCT p FROM Player p " +
		"JOIN GameParticipant gp ON gp.player = p " +
		"JOIN gp.game g " +
		"JOIN g.league l " +
		"WHERE l.leagueName = :leagueName")
	List<Player> findPlayersByLeagueName(@Param("leagueName") String leagueName);

	@Query("""
			SELECT DISTINCT p
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND (:teamId IS NULL OR t.id = :teamId)
			ORDER BY p.name
			""")
	List<Player> findOnboardingPlayers(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId);

	/**
	 * 해당 리그·시즌에서 선수별로 "가장 최근 경기"의 팀 하나로 중복 제거해 페이지로 반환한다.
	 *
	 * <p>예전에는 각 행마다 상관 서브쿼리({@code = MAX(...)})로 최신 경기 시각을 구해
	 * 선수 수의 수십 배에 달하는 중첩 반복이 일어나 페이지당 8~9초가 걸렸다. 이를 윈도우 함수
	 * {@code ROW_NUMBER()}로 바꿔 참가 기록을 한 번만 스캔하도록 했다(동일 데이터 기준 ~55배 단축).
	 * 또한 동일 시각 경기가 둘이어도 {@code game_id} 타이브레이크로 한 행만 남아 중복이 사라진다.
	 *
	 * <p>윈도우 함수와 파생 테이블을 쓰기 위해 네이티브 쿼리로 작성했다. 컬럼 별칭은
	 * {@link LckPlayerOption} 프로젝션 게터명과 일치시킨다.
	 */
	@Query(
			value = """
					SELECT playerId, playerName, playerImageUrl, role,
					       teamId, teamCode, teamName, teamImageUrl
					FROM (
						SELECT
							p.player_id AS playerId,
							p.player_name AS playerName,
							p.image_url AS playerImageUrl,
							p.role AS role,
							t.team_id AS teamId,
							t.team_code AS teamCode,
							t.team_name AS teamName,
							t.team_image_url AS teamImageUrl,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						WHERE l.league_name = :leagueName
						  AND l.season_year = :year
					) ranked
					WHERE ranked.rn = 1
					  AND (:teamId IS NULL OR ranked.teamId = :teamId)
					  AND (:query IS NULL OR LOWER(ranked.playerName) LIKE LOWER(CONCAT('%', :query, '%')))
					ORDER BY CASE UPPER(ranked.role)
					             WHEN 'TOP' THEN 1
					             WHEN 'JUNGLE' THEN 2
					             WHEN 'MID' THEN 3
					             WHEN 'ADC' THEN 4
					             WHEN 'SUPPORT' THEN 5
					             ELSE 6 END,
					         ranked.playerName
					""",
			countQuery = """
					SELECT COUNT(*)
					FROM (
						SELECT
							p.player_name AS playerName,
							t.team_id AS teamId,
							ROW_NUMBER() OVER (
								PARTITION BY p.player_id
								ORDER BY g.actual_game_start_time DESC, g.game_id DESC
							) AS rn
						FROM game_participants gp
						JOIN games g ON gp.game_id = g.game_id
						JOIN leagues l ON g.league_id = l.league_id
						JOIN players p ON gp.player_id = p.player_id
						JOIN teams t ON gp.team_id = t.team_id
						WHERE l.league_name = :leagueName
						  AND l.season_year = :year
					) ranked
					WHERE ranked.rn = 1
					  AND (:teamId IS NULL OR ranked.teamId = :teamId)
					  AND (:query IS NULL OR LOWER(ranked.playerName) LIKE LOWER(CONCAT('%', :query, '%')))
					""",
			nativeQuery = true)
	Page<LckPlayerOption> findLckPlayerOptions(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId,
			@Param("query") String query,
			Pageable pageable);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id = :playerId
			ORDER BY t.name
			""")
	List<LckPlayerOption> findLckPlayerOption(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerId") Long playerId);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id IN :playerIds
			ORDER BY p.name, t.name
			""")
	List<LckPlayerOption> findLckPlayerOptionsByPlayerIds(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerIds") Set<Long> playerIds);

	interface LckPlayerOption {
		Long getPlayerId();

		String getPlayerName();

		String getPlayerImageUrl();

		String getRole();

		Long getTeamId();

		String getTeamCode();

		String getTeamName();

		String getTeamImageUrl();
	}
}
