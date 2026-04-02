package com.toy.nar.domain.participant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.domain.participant.entity.GameTeamStat;

public interface GameTeamStatRepository extends JpaRepository<GameTeamStat, Long> {

	List<GameTeamStat> findByGameId(Long id);

	/**
	 * 특정 팀의 특정 연도 경기 통계 조회
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				WHERE gts.team.id = :teamId
				AND YEAR(g.actualGameStartTime) = :year
				ORDER BY g.actualGameStartTime
			""")
	List<GameTeamStat> findByTeamIdAndYear(@Param("teamId") Long teamId, @Param("year") int year);

	/**
	 * 특정 연도 모든 팀 통계 조회 (리그 평균 계산용)
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				WHERE YEAR(g.actualGameStartTime) = :year
			""")
	List<GameTeamStat> findByYear(@Param("year") int year);

	/**
	 * 특정 팀의 특정 연도 게임 ID 목록 조회
	 */
	@Query("""
				SELECT DISTINCT g.id FROM GameTeamStat gts
				JOIN gts.game g
				WHERE gts.team.id = :teamId
				AND YEAR(g.actualGameStartTime) = :year
			""")
	List<Long> findGameIdsByTeamIdAndYear(@Param("teamId") Long teamId, @Param("year") int year);

	/**
	 * LCK 리그만 필터링하여 조회
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				JOIN g.league l
				WHERE YEAR(g.actualGameStartTime) = :year
				AND l.leagueName = :leagueName
			""")
	List<GameTeamStat> findByYearAndLeagueName(@Param("year") int year, @Param("leagueName") String leagueName);

	/**
	 * 필터 기반 팀별 승률 통계 조회 (팀 랭킹용)
	 */
	@Query(value = """
			SELECT t.team_id, t.team_name, t.team_code, t.team_image_url,
			       SUM(CASE WHEN gts.result = 1 THEN 1 ELSE 0 END) as wins,
			       COUNT(*) as total_games
			FROM game_team_stat gts
			JOIN teams t ON gts.team_id = t.team_id
			JOIN games g ON gts.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			LEFT JOIN game_participants gp ON gp.game_id = g.game_id AND gp.team_id = t.team_id
			WHERE (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:leagueNames IS NULL OR l.league_name IN (:leagueNames))
			  AND (:splits IS NULL OR l.season_split IN (:splits))
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR gp.side = :side)
			GROUP BY t.team_id, t.team_name, t.team_code, t.team_image_url
			ORDER BY wins / total_games DESC, wins DESC
			""", nativeQuery = true)
	List<Object[]> findTeamStatsByFilter(
			@Param("year") Integer year,
			@Param("leagueNames") List<String> leagueNames,
			@Param("splits") List<String> splits,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 필터 기반 전체 게임 수 조회 (밴률 계산용)
	 */
	@Query(value = """
			SELECT COUNT(DISTINCT g.game_id)
			FROM games g
			JOIN leagues l ON g.league_id = l.league_id
			LEFT JOIN game_participants gp ON gp.game_id = g.game_id
			WHERE (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:leagueNames IS NULL OR l.league_name IN (:leagueNames))
			  AND (:splits IS NULL OR l.season_split IN (:splits))
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR gp.side = :side)
			""", nativeQuery = true)
	long countGamesByFilter(
			@Param("year") Integer year,
			@Param("leagueNames") List<String> leagueNames,
			@Param("splits") List<String> splits,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 팀 스캐터 차트용 집계 조회.
	 * - avg_all: 경기당 평균 종합 포인트(킬 + 드래곤*2 + 타워)
	 * - avg_kills: 경기당 평균 킬
	 * - avg_gold: 경기당 평균 팀 획득 골드(참가자 5인 earned_gold 합)
	 * - avg_objectives: 경기당 평균 오브젝트(드래곤+전령+바론+타워+공허유충+억제기+아타칸)
	 */
	@Query(value = """
			SELECT
				t.team_id,
				t.team_name,
				t.team_code,
				t.team_image_url,
				COUNT(*) AS games_played,
				SUM(CASE WHEN gts.result = 1 THEN 1 ELSE 0 END) AS wins,
				AVG(
					COALESCE(gts.team_kills, 0) +
					(COALESCE(gts.dragons, 0) * 2) +
					COALESCE(gts.towers, 0)
				) AS avg_all,
				AVG(COALESCE(gts.team_kills, 0)) AS avg_kills,
				AVG(COALESCE(tg.total_gold, 0)) AS avg_gold,
				AVG(
					COALESCE(gts.dragons, 0) +
					COALESCE(gts.heralds, 0) +
					COALESCE(gts.barons, 0) +
					COALESCE(gts.towers, 0) +
					COALESCE(gts.void_grubs, 0) +
					COALESCE(gts.inhibitors, 0) +
					COALESCE(gts.atakhans, 0)
				) AS avg_objectives
			FROM game_team_stat gts
			JOIN teams t ON gts.team_id = t.team_id
			JOIN games g ON gts.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			LEFT JOIN (
				SELECT gp.game_id, gp.team_id, SUM(COALESCE(gps.earned_gold, 0)) AS total_gold
				FROM game_participants gp
				JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
				GROUP BY gp.game_id, gp.team_id
			) tg ON tg.game_id = g.game_id AND tg.team_id = t.team_id
			WHERE YEAR(g.actual_game_start_time) = :year
			  AND l.league_name = :leagueName
			GROUP BY t.team_id, t.team_name, t.team_code, t.team_image_url
			""", nativeQuery = true)
	List<Object[]> findTeamScatterStatsByLeagueAndYear(
			@Param("year") int year,
			@Param("leagueName") String leagueName);

	/**
	 * 팀별 상세 지표 집계 (세트 기준).
	 */
	@Query(value = """
			SELECT
				t.team_id,
				t.team_name,
				t.team_code,
				t.team_image_url,
				COUNT(*) AS sets_played,
				SUM(CASE WHEN gts.result = 1 THEN 1 ELSE 0 END) AS set_wins,
				SUM(CASE WHEN gts.result = 0 THEN 1 ELSE 0 END) AS set_losses,
				AVG(COALESCE(gts.team_kills, 0)) AS avg_kills,
				AVG(COALESCE(tg.total_gold, 0)) AS avg_gold,
				AVG(COALESCE(gts.barons, 0)) AS avg_barons,
				AVG(COALESCE(gts.dragons, 0)) AS avg_dragons,
				AVG(COALESCE(gts.towers, 0)) AS avg_towers,
				SUM(CASE WHEN gts.is_first_blood = 1 THEN 1 ELSE 0 END) AS first_blood_count,
				SUM(CASE WHEN gts.is_first_tower = 1 THEN 1 ELSE 0 END) AS first_tower_count,
				SUM(CASE WHEN gts.is_first_dragon = 1 THEN 1 ELSE 0 END) AS first_dragon_count,
				SUM(CASE WHEN gts.is_first_herald = 1 THEN 1 ELSE 0 END) AS first_herald_count,
				SUM(CASE WHEN gts.is_first_baron = 1 THEN 1 ELSE 0 END) AS first_baron_count
			FROM game_team_stat gts
			JOIN teams t ON gts.team_id = t.team_id
			JOIN games g ON gts.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			LEFT JOIN (
				SELECT gp.game_id, gp.team_id, SUM(COALESCE(gps.earned_gold, 0)) AS total_gold
				FROM game_participants gp
				JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
				GROUP BY gp.game_id, gp.team_id
			) tg ON tg.game_id = g.game_id AND tg.team_id = t.team_id
			WHERE YEAR(g.actual_game_start_time) = :year
			  AND l.league_name = :leagueName
			GROUP BY t.team_id, t.team_name, t.team_code, t.team_image_url
			""", nativeQuery = true)
	List<Object[]> findTeamDetailStatsByLeagueAndYear(
			@Param("year") int year,
			@Param("leagueName") String leagueName);

	/**
	 * 팀별 매치(시리즈) 전적 집계.
	 * 동일 팀 조합 + 동일 일자를 하나의 매치로 간주해 세트 승패를 합산한다.
	 */
	@Query(value = """
			WITH per_set AS (
				SELECT
					gts.team_id AS team_id,
					opp.team_id AS opp_team_id,
					DATE(g.actual_game_start_time) AS match_day,
					CASE WHEN gts.result = 1 THEN 1 ELSE 0 END AS set_win,
					CASE WHEN gts.result = 0 THEN 1 ELSE 0 END AS set_loss
				FROM game_team_stat gts
				JOIN game_team_stat opp
					ON opp.game_id = gts.game_id
					AND opp.team_id <> gts.team_id
				JOIN games g ON g.game_id = gts.game_id
				JOIN leagues l ON l.league_id = g.league_id
				WHERE YEAR(g.actual_game_start_time) = :year
				  AND l.league_name = :leagueName
			),
			series AS (
				SELECT
					team_id,
					opp_team_id,
					match_day,
					SUM(set_win) AS set_wins,
					SUM(set_loss) AS set_losses
				FROM per_set
				GROUP BY team_id, opp_team_id, match_day
			)
			SELECT
				team_id,
				COUNT(*) AS matches_played,
				SUM(CASE WHEN set_wins > set_losses THEN 1 ELSE 0 END) AS match_wins,
				SUM(CASE WHEN set_wins < set_losses THEN 1 ELSE 0 END) AS match_losses
			FROM series
			GROUP BY team_id
			""", nativeQuery = true)
	List<Object[]> findTeamSeriesStatsByLeagueAndYear(
			@Param("year") int year,
			@Param("leagueName") String leagueName);

	/**
	 * 팀 페이지 대시보드 - 게임 요약(세트 기준) 집계.
	 */
	@Query(value = """
			SELECT
				COUNT(*) AS sets_played,
				SUM(CASE WHEN gts.result = 1 THEN 1 ELSE 0 END) AS set_wins,
				SUM(CASE WHEN gts.result = 0 THEN 1 ELSE 0 END) AS set_losses,
				AVG(COALESCE(gts.team_kills, 0)) AS avg_kills,
				AVG(COALESCE(tg.total_gold, 0)) AS avg_gold,
				AVG(COALESCE(g.game_length_seconds, 0)) AS avg_game_length_seconds,
				AVG(COALESCE(gts.barons, 0)) AS avg_barons,
				AVG(COALESCE(gts.dragons, 0)) AS avg_dragons,
				AVG(COALESCE(gts.towers, 0)) AS avg_towers,
				SUM(CASE WHEN gts.is_first_blood = 1 THEN 1 ELSE 0 END) AS first_blood_count,
				SUM(CASE WHEN gts.is_first_tower = 1 THEN 1 ELSE 0 END) AS first_tower_count,
				SUM(CASE WHEN gts.is_first_dragon = 1 THEN 1 ELSE 0 END) AS first_dragon_count,
				SUM(CASE WHEN gts.is_first_herald = 1 THEN 1 ELSE 0 END) AS first_herald_count,
				SUM(CASE WHEN gts.is_first_baron = 1 THEN 1 ELSE 0 END) AS first_baron_count
			FROM game_team_stat gts
			JOIN games g ON g.game_id = gts.game_id
			JOIN leagues l ON l.league_id = g.league_id
			JOIN (
				SELECT DISTINCT gp.game_id, gp.team_id, UPPER(gp.side) AS side
				FROM game_participants gp
			) gs ON gs.game_id = g.game_id AND gs.team_id = gts.team_id
			LEFT JOIN (
				SELECT gp.game_id, gp.team_id, SUM(COALESCE(gps.earned_gold, 0)) AS total_gold
				FROM game_participants gp
				JOIN game_player_stat gps ON gps.game_participant_id = gp.participant_game_id
				GROUP BY gp.game_id, gp.team_id
			) tg ON tg.game_id = g.game_id AND tg.team_id = gts.team_id
			WHERE gts.team_id = :teamId
			  AND l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			  AND (:side IS NULL OR gs.side = :side)
			""", nativeQuery = true)
	List<Object[]> findTeamDashboardSummary(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);

	/**
	 * 팀 페이지 대시보드 - 매치(시리즈) 전적 집계.
	 * 동일 상대 + 동일 날짜를 1개 매치로 간주한다.
	 */
	@Query(value = """
			WITH per_set AS (
				SELECT
					opp.team_id AS opp_team_id,
					DATE(g.actual_game_start_time) AS match_day,
					CASE WHEN gts.result = 1 THEN 1 ELSE 0 END AS set_win,
					CASE WHEN gts.result = 0 THEN 1 ELSE 0 END AS set_loss
				FROM game_team_stat gts
				JOIN game_team_stat opp
					ON opp.game_id = gts.game_id
					AND opp.team_id <> gts.team_id
				JOIN games g ON g.game_id = gts.game_id
				JOIN leagues l ON l.league_id = g.league_id
				JOIN (
					SELECT DISTINCT gp.game_id, gp.team_id, UPPER(gp.side) AS side
					FROM game_participants gp
				) gs ON gs.game_id = g.game_id AND gs.team_id = gts.team_id
				WHERE gts.team_id = :teamId
				  AND l.league_name = :leagueName
				  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
				  AND (:split IS NULL OR l.season_split = :split)
				  AND (:patch IS NULL OR g.patch = :patch)
				  AND (:side IS NULL OR gs.side = :side)
			),
			series AS (
				SELECT
					opp_team_id,
					match_day,
					SUM(set_win) AS set_wins,
					SUM(set_loss) AS set_losses
				FROM per_set
				GROUP BY opp_team_id, match_day
			)
			SELECT
				COUNT(*) AS matches_played,
				SUM(CASE WHEN set_wins > set_losses THEN 1 ELSE 0 END) AS match_wins,
				SUM(CASE WHEN set_wins < set_losses THEN 1 ELSE 0 END) AS match_losses
			FROM series
			""", nativeQuery = true)
	List<Object[]> findTeamDashboardSeriesSummary(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch,
			@Param("side") String side);
}
