package com.toy.nar.domain.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;

import com.toy.nar.domain.game.entity.GameParticipant;

public interface GameParticipantRepository
		extends JpaRepository<GameParticipant, Long>, GameParticipantRepositoryCustom {

	@Query("SELECT gp FROM GameParticipant gp " +
			"JOIN FETCH gp.game g " +
			"JOIN FETCH gp.champion c " +
			"JOIN FETCH gp.team t " +
			"JOIN FETCH gp.player p " +
			"LEFT JOIN FETCH g.league l " +
			"WHERE g.id IN :gameIds " +
			"ORDER BY g.actualGameStartTime DESC, gp.position ASC")
	List<GameParticipant> findGameDetailsByGameIds(@Param("gameIds") Set<Long> gameIds);

	@Query("SELECT DISTINCT gp.game.league, gp.team FROM GameParticipant gp " +
			"JOIN gp.game.league l " +
			"JOIN gp.team t " +
			"ORDER BY l.seasonYear DESC, l.seasonSplit, t.name")
	List<Object[]> findDistinctLeagueTeamPairs();

	@Query(value = """
			SELECT g.game_id, COUNT(gp.participant_game_id) as participant_count
			FROM games g
			LEFT JOIN game_participants gp ON g.game_id = gp.game_id
			GROUP BY g.game_id
			HAVING COUNT(gp.participant_game_id) != 10
			ORDER BY participant_count DESC
			LIMIT 100
			""", nativeQuery = true)
	List<Object[]> findIncompleteGames();

	@Query("SELECT DISTINCT gp.game.league.id, gp.team.id FROM GameParticipant gp")
	List<Object[]> findAllDistinctLeagueTeamPairs();

	@Query("SELECT gp FROM GameParticipant gp " +
			"JOIN FETCH gp.game g " +
			"JOIN FETCH gp.champion c " +
			"JOIN FETCH gp.team t " +
			"LEFT JOIN FETCH g.league l " +
			"WHERE gp.game.id IN (" +
			"    SELECT p1.game.id FROM GameParticipant p1 " +
			"    JOIN GameParticipant p2 ON p1.game.id = p2.game.id " +
			"    WHERE p1.champion.championNameEn = :champion1 " +
			"    AND p2.champion.championNameEn = :champion2 " +
			"    AND p1.team.id != p2.team.id" +
			") " +
			"AND (:year IS NULL OR l.seasonYear = :year) " + // 기존 필터 적용
			"AND (:splits IS NULL OR l.seasonSplit IN :splits) " +
			"AND (:leagueNames IS NULL OR l.leagueName IN :leagueNames) " +
			"AND (:teamNames IS NULL OR t.name IN :teamNames) " +
			"AND (:patch IS NULL OR g.patch = :patch) " +
			"ORDER BY g.actualGameStartTime DESC") // 최신순 기본 정렬
	List<GameParticipant> find1v1MatchupParticipants(
			@Param("champion1") String champion1,
			@Param("champion2") String champion2,
			@Param("year") Integer year,
			@Param("splits") List<String> splits,
			@Param("leagueNames") List<String> leagueNames,
			@Param("teamNames") List<String> teamNames,
			@Param("patch") String patch);

	@Query("SELECT gp FROM GameParticipant gp " +
			"JOIN FETCH gp.player " +
			"JOIN FETCH gp.champion " +
			"JOIN FETCH gp.team " +
			"WHERE gp.game.id IN :gameIds")
	List<GameParticipant> findWithDetailsByGameIds(@Param("gameIds") List<Long> gameIds);

	/**
	 * 팀의 게임별 참가자 + stat 조회 (골드차 계산용)
	 */
	@Query("""
				SELECT gp FROM GameParticipant gp
				JOIN FETCH gp.stat s
				JOIN FETCH gp.game g
				WHERE gp.team.id = :teamId
				AND YEAR(g.actualGameStartTime) = :year
			""")
	List<GameParticipant> findByTeamIdAndYearWithStats(@Param("teamId") Long teamId, @Param("year") int year);

	/**
	 * 팀-포지션별 모스트 픽 조회 (팀 랭킹용)
	 */
	@Query(value = """
			SELECT t.team_id, gp.position, c.champion_name_en, COUNT(*) as pick_count,
			       c.image_url, SUM(CASE WHEN gp.is_win = 1 THEN 1 ELSE 0 END) as wins
			FROM game_participants gp
			JOIN teams t ON gp.team_id = t.team_id
			JOIN champions c ON gp.champion_id = c.champion_id
			JOIN games g ON gp.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			WHERE (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:leagueNames IS NULL OR l.league_name IN (:leagueNames))
			  AND (:splits IS NULL OR l.season_split IN (:splits))
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR gp.side = :side)
			GROUP BY t.team_id, gp.position, c.champion_id, c.champion_name_en, c.image_url
			ORDER BY t.team_id, gp.position, pick_count DESC
			""", nativeQuery = true)
	List<Object[]> findMostPicksByTeamAndPosition(
			@Param("year") Integer year,
			@Param("leagueNames") List<String> leagueNames,
			@Param("splits") List<String> splits,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 팀-챔피언별 상대 챔피언 매치업 조회 (호버 시 상대 챔피언 표시용)
	 * 같은 게임, 같은 포지션의 상대팀 챔피언 카운트
	 */
	@Query(value = """
			SELECT gp.team_id, gp.position, c1.champion_name_en as my_champion,
			       c2.champion_name_en as opponent_champion, c2.image_url as opponent_image_url,
			       COUNT(*) as match_count,
			       SUM(CASE WHEN gp.is_win = 1 THEN 1 ELSE 0 END) as wins
			FROM game_participants gp
			JOIN game_participants opp ON gp.game_id = opp.game_id
			                           AND gp.position = opp.position
			                           AND gp.team_id != opp.team_id
			JOIN champions c1 ON gp.champion_id = c1.champion_id
			JOIN champions c2 ON opp.champion_id = c2.champion_id
			JOIN teams t ON gp.team_id = t.team_id
			JOIN games g ON gp.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			WHERE (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:leagueNames IS NULL OR l.league_name IN (:leagueNames))
			  AND (:splits IS NULL OR l.season_split IN (:splits))
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR gp.side = :side)
			GROUP BY gp.team_id, gp.position, c1.champion_id, c1.champion_name_en,
			         c2.champion_id, c2.champion_name_en, c2.image_url
			ORDER BY gp.team_id, gp.position, c1.champion_name_en, match_count DESC
			""", nativeQuery = true)
	List<Object[]> findOpponentMatchupsByTeamAndPosition(
			@Param("year") Integer year,
			@Param("leagueNames") List<String> leagueNames,
			@Param("splits") List<String> splits,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 팀 페이지 - 선수 기록 집계.
	 */
	@Query(value = """
			SELECT
				p.player_id,
				p.player_name,
				p.image_url,
				gp.position,
				COUNT(*) AS games_played,
				SUM(CASE WHEN gp.is_win = 1 THEN 1 ELSE 0 END) AS wins,
				SUM(COALESCE(gps.kills, 0)) AS total_kills,
				SUM(COALESCE(gps.deaths, 0)) AS total_deaths,
				SUM(COALESCE(gps.assists, 0)) AS total_assists,
				SUM(CASE WHEN gps.is_first_blood_kill = 1 THEN 1 ELSE 0 END) AS first_kill_count,
				SUM(CASE WHEN gps.is_first_blood_victim = 1 THEN 1 ELSE 0 END) AS first_death_count,
				SUM(COALESCE(gps.penta_kills, 0)) AS penta_kill_count,
				AVG(
					CASE
						WHEN COALESCE(gts.team_kills, 0) > 0
							THEN ((COALESCE(gps.kills, 0) + COALESCE(gps.assists, 0)) * 100.0) / gts.team_kills
						ELSE 0
					END
				) AS avg_kill_participation_pct,
				AVG(COALESCE(gps.damage_share, 0)) * 100 AS avg_damage_share_pct,
				AVG(COALESCE(gps.earned_gold_share, 0)) * 100 AS avg_gold_share_pct,
				AVG(COALESCE(gps.vision_score, 0)) AS avg_vision_score,
				AVG(COALESCE(gps.vspm, 0)) AS avg_vspm
			FROM game_participants gp
			JOIN players p ON p.player_id = gp.player_id
			JOIN games g ON g.game_id = gp.game_id
			JOIN leagues l ON l.league_id = g.league_id
			JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
			JOIN game_team_stat gts ON gts.game_id = gp.game_id AND gts.team_id = gp.team_id
			WHERE gp.team_id = :teamId
			  AND l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR UPPER(gp.side) = :side)
			GROUP BY p.player_id, p.player_name, p.image_url, gp.position
			ORDER BY
				CASE gp.position
					WHEN 'top' THEN 1
					WHEN 'jng' THEN 2
					WHEN 'mid' THEN 3
					WHEN 'bot' THEN 4
					WHEN 'sup' THEN 5
					ELSE 99
				END,
				games_played DESC
			""", nativeQuery = true)
	List<Object[]> findTeamPlayerRecords(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 팀 페이지 - 플레이한 챔피언 집계(선수별).
	 */
	@Query(value = """
			SELECT
				p.player_id,
				p.player_name,
				p.image_url,
				gp.position,
				c.champion_id,
				c.champion_name_kr,
				c.champion_name_en,
				c.image_url AS champion_image_url,
				COUNT(*) AS games_played,
				SUM(CASE WHEN gp.is_win = 1 THEN 1 ELSE 0 END) AS wins,
				SUM(COALESCE(gps.kills, 0)) AS total_kills,
				SUM(COALESCE(gps.deaths, 0)) AS total_deaths,
				SUM(COALESCE(gps.assists, 0)) AS total_assists,
				MAX(g.actual_game_start_time) AS last_used_at
			FROM game_participants gp
			JOIN players p ON p.player_id = gp.player_id
			JOIN champions c ON c.champion_id = gp.champion_id
			JOIN games g ON g.game_id = gp.game_id
			JOIN leagues l ON l.league_id = g.league_id
			JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
			WHERE gp.team_id = :teamId
			  AND l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR UPPER(gp.side) = :side)
			GROUP BY
				p.player_id, p.player_name, p.image_url, gp.position,
				c.champion_id, c.champion_name_kr, c.champion_name_en, c.image_url
			ORDER BY
				CASE gp.position
					WHEN 'top' THEN 1
					WHEN 'jng' THEN 2
					WHEN 'mid' THEN 3
					WHEN 'bot' THEN 4
					WHEN 'sup' THEN 5
					ELSE 99
				END,
				p.player_id,
				games_played DESC,
				last_used_at DESC
			""", nativeQuery = true)
	List<Object[]> findTeamPlayedChampions(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);
}
