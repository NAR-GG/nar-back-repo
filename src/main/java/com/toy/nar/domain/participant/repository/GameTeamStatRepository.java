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
}
