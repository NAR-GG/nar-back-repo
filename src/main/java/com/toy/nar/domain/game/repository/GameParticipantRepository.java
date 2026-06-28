package com.toy.nar.domain.game.repository;

import com.toy.nar.app.schedule.dto.GameDetailParticipantRow;
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

	@Query("SELECT new com.toy.nar.app.schedule.dto.GameDetailParticipantRow(" +
			"   g.id, g.gameNumber, g.gameLengthSeconds, gp.side, gp.position, gp.isWin, " +
			"   t.name, p.name, c.championNameEn) " +
			"FROM GameParticipant gp " +
			"JOIN gp.game g " +
			"JOIN gp.champion c " +
			"JOIN gp.team t " +
			"JOIN gp.player p " +
			"WHERE g.id IN :gameIds " +
			"ORDER BY g.gameNumber ASC, gp.side ASC, gp.position ASC")
	List<GameDetailParticipantRow> findScheduleDetailRowsByGameIds(@Param("gameIds") Set<Long> gameIds);

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

	@Query("""
				SELECT gp FROM GameParticipant gp
				LEFT JOIN FETCH gp.stat s
				JOIN FETCH gp.game g
				JOIN FETCH gp.team t
				JOIN g.league l
				WHERE l.leagueName = :leagueName
				AND (:year IS NULL OR YEAR(g.actualGameStartTime) = :year)
				AND (:split IS NULL OR l.seasonSplit = :split)
				AND (:patch IS NULL OR g.patch = :patch)
				AND (:side IS NULL OR UPPER(gp.side) = :side)
				ORDER BY g.actualGameStartTime
			""")
	List<GameParticipant> findByFilterWithStats(
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);

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
					MAX(g.actual_game_start_time) AS last_used_at,
					UPPER(gp.side) AS side
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
				GROUP BY p.player_id, p.player_name, p.image_url, gp.position,
				         c.champion_id, c.champion_name_kr, c.champion_name_en, c.image_url, UPPER(gp.side)
				ORDER BY games_played DESC, c.champion_name_en
				""", nativeQuery = true)
	List<Object[]> findTeamPlayedChampionsGroupedBySide(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch);

	/**
	 * 선수 카드 목록용 선수 수 집계.
	 */
	@Query(value = """
			SELECT COUNT(DISTINCT gp.player_id)
			FROM game_participants gp
			JOIN games g ON g.game_id = gp.game_id
			JOIN leagues l ON l.league_id = g.league_id
			WHERE l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR UPPER(gp.side) = :side)
			""", nativeQuery = true)
	long countDistinctPlayersByFilter(
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 선수 카드 목록용 선수 집계.
	 * 필터 구간 내 출전 수가 가장 많은 팀/포지션을 선수 대표값으로 사용한다.
	 */
	@Query(value = """
			WITH filtered_team_games AS (
				SELECT
					gts.team_id,
					gts.result
				FROM game_team_stat gts
				JOIN games g ON g.game_id = gts.game_id
				JOIN leagues l ON l.league_id = g.league_id
				JOIN (
					SELECT DISTINCT gp.game_id, gp.team_id, UPPER(gp.side) AS side
					FROM game_participants gp
				) gs ON gs.game_id = g.game_id AND gs.team_id = gts.team_id
				WHERE l.league_name = :leagueName
				  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
				  AND (:split IS NULL OR l.season_split = :split)
				  AND (:patch IS NULL OR g.patch = :patch)
				  AND (:side IS NULL OR gs.side = :side)
			),
			team_rank AS (
				SELECT
					ftg.team_id,
					ROW_NUMBER() OVER (
						ORDER BY
							(SUM(CASE WHEN ftg.result = 1 THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0)) DESC,
							SUM(CASE WHEN ftg.result = 1 THEN 1 ELSE 0 END) DESC,
							ftg.team_id ASC
					) AS team_rank
				FROM filtered_team_games ftg
				GROUP BY ftg.team_id
			),
			player_team AS (
				SELECT
					p.player_id,
					p.player_name,
					p.image_url AS player_image_url,
					p.real_name,
					p.birth_date,
					p.game_accounts,
					gp.position,
					t.team_id,
					t.team_code,
					t.team_image_url,
					COALESCE(tr.team_rank, 9999) AS team_rank,
					COUNT(*) AS games_played,
					SUM(COALESCE(gps.kills, 0)) AS total_kills,
					SUM(COALESCE(gps.deaths, 0)) AS total_deaths,
					SUM(COALESCE(gps.assists, 0)) AS total_assists,
					AVG(COALESCE(gps.earned_gpm, 0)) AS avg_gpm,
					AVG(COALESCE(gps.dpm, 0)) AS avg_dpm
				FROM game_participants gp
				JOIN players p ON p.player_id = gp.player_id
				JOIN teams t ON t.team_id = gp.team_id
				JOIN games g ON g.game_id = gp.game_id
				JOIN leagues l ON l.league_id = g.league_id
				JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
				LEFT JOIN team_rank tr ON tr.team_id = t.team_id
				WHERE l.league_name = :leagueName
				  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
				  AND (:split IS NULL OR l.season_split = :split)
				  AND (:patch IS NULL OR g.patch = :patch)
				  AND (:side IS NULL OR UPPER(gp.side) = :side)
				GROUP BY
					p.player_id, p.player_name, p.image_url, p.real_name, p.birth_date, p.game_accounts,
					gp.position, t.team_id, t.team_code, t.team_image_url, tr.team_rank
			),
			ranked AS (
				SELECT
					pt.*,
					ROW_NUMBER() OVER (PARTITION BY pt.player_id ORDER BY pt.games_played DESC, pt.team_rank ASC, pt.team_id ASC) AS rn
				FROM player_team pt
			)
			SELECT
				player_id,
				player_name,
				player_image_url,
				real_name,
				birth_date,
				game_accounts,
				position,
				team_code,
				team_image_url,
				team_rank,
				games_played,
				total_kills,
				total_deaths,
				total_assists,
				avg_gpm,
				avg_dpm
			FROM ranked
			WHERE rn = 1
			ORDER BY
				team_rank ASC,
				CASE position
					WHEN 'top' THEN 1
					WHEN 'jng' THEN 2
					WHEN 'mid' THEN 3
					WHEN 'bot' THEN 4
					WHEN 'sup' THEN 5
					ELSE 99
				END,
				player_name
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Object[]> findPlayerCardSummariesByFilter(
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * 선수 카드 목록용 선수별 모스트 챔피언 집계.
	 */
	@Query(value = """
			SELECT
				gp.player_id,
				c.champion_id,
				c.champion_name_kr,
				c.champion_name_en,
				c.image_url,
				COUNT(*) AS games_played,
				SUM(CASE WHEN gp.is_win = 1 THEN 1 ELSE 0 END) AS wins,
				c.loading_image_url
			FROM game_participants gp
			JOIN champions c ON c.champion_id = gp.champion_id
			JOIN games g ON g.game_id = gp.game_id
			JOIN leagues l ON l.league_id = g.league_id
			WHERE gp.player_id IN (:playerIds)
			  AND l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR UPPER(gp.side) = :side)
			GROUP BY gp.player_id, c.champion_id, c.champion_name_kr, c.champion_name_en, c.image_url, c.loading_image_url
			ORDER BY gp.player_id, games_played DESC, wins DESC, c.champion_name_en
			""", nativeQuery = true)
	List<Object[]> findPlayerMostChampionsByFilter(
			@Param("playerIds") List<Long> playerIds,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);
}
