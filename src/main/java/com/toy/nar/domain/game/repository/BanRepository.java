package com.toy.nar.domain.game.repository;

import com.toy.nar.app.schedule.dto.GameBanRow;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.domain.game.entity.Ban;
import com.toy.nar.domain.game.entity.Game;

public interface BanRepository extends JpaRepository<Ban, Long> {
	boolean existsByGame(Game game);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	int deleteByGameIdIn(Set<Long> gameIds);

	@Query("SELECT new com.toy.nar.app.schedule.dto.GameBanRow(" +
			"   b.game.id, b.team.name, b.bannedChampion.championNameEn) " +
			"FROM Ban b " +
			"WHERE b.game.id IN :gameIds")
	List<GameBanRow> findScheduleBanRowsByGameIds(@Param("gameIds") Set<Long> gameIds);

	/**
	 * 필터 기반 챔피언별 밴 횟수 조회
	 */
	@Query(value = """
			SELECT c.champion_name_en, COUNT(*) as ban_count
			FROM bans b
			JOIN champions c ON b.banned_champion_id = c.champion_id
			JOIN games g ON b.game_id = g.game_id
			JOIN leagues l ON g.league_id = l.league_id
			WHERE (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:leagueNames IS NULL OR l.league_name IN (:leagueNames))
			  AND (:splits IS NULL OR l.season_split IN (:splits))
			  AND (:patch IS NULL OR g.patch = :patch)
			GROUP BY c.champion_id, c.champion_name_en
			""", nativeQuery = true)
	List<Object[]> findBanCountsByFilter(
			@Param("year") Integer year,
			@Param("leagueNames") List<String> leagueNames,
			@Param("splits") List<String> splits,
			@Param("patch") String patch);

	/**
	 * 팀이 밴한 챔피언 집계.
	 */
	@Query(value = """
			SELECT
				c.champion_id,
				c.champion_name_kr,
				c.champion_name_en,
				c.image_url,
				gs.side,
				COUNT(*) AS ban_count
			FROM bans b
			JOIN champions c ON c.champion_id = b.banned_champion_id
			JOIN games g ON g.game_id = b.game_id
			JOIN leagues l ON l.league_id = g.league_id
			JOIN (
				SELECT DISTINCT gp.game_id, gp.team_id, UPPER(gp.side) AS side
				FROM game_participants gp
				WHERE gp.team_id = :teamId
			) gs ON gs.game_id = b.game_id AND gs.team_id = b.team_id
			WHERE b.team_id = :teamId
			  AND l.league_name = :leagueName
			  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
			  AND (:split IS NULL OR l.season_split = :split)
			  AND (:patch IS NULL OR g.patch = :patch)
			GROUP BY c.champion_id, c.champion_name_kr, c.champion_name_en, c.image_url, gs.side
			ORDER BY ban_count DESC, c.champion_name_en
			""", nativeQuery = true)
	List<Object[]> findBansByTeamGroupedBySide(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch);

	/**
	 * 팀이 상대에게 밴당한 챔피언 집계.
	 */
	@Query(value = """
			WITH my_games AS (
				SELECT DISTINCT gp.game_id, UPPER(gp.side) AS side
				FROM game_participants gp
				JOIN games g ON g.game_id = gp.game_id
				JOIN leagues l ON l.league_id = g.league_id
				WHERE gp.team_id = :teamId
				  AND l.league_name = :leagueName
				  AND (:year IS NULL OR YEAR(g.actual_game_start_time) = :year)
				  AND (:split IS NULL OR l.season_split = :split)
				  AND (:patch IS NULL OR g.patch = :patch)
			)
			SELECT
				c.champion_id,
				c.champion_name_kr,
				c.champion_name_en,
				c.image_url,
				mg.side,
				COUNT(*) AS ban_count
			FROM my_games mg
			JOIN bans b ON b.game_id = mg.game_id AND b.team_id <> :teamId
			JOIN champions c ON c.champion_id = b.banned_champion_id
			GROUP BY c.champion_id, c.champion_name_kr, c.champion_name_en, c.image_url, mg.side
			ORDER BY ban_count DESC, c.champion_name_en
			""", nativeQuery = true)
	List<Object[]> findBansAgainstTeamGroupedBySide(
			@Param("teamId") Long teamId,
			@Param("leagueName") String leagueName,
			@Param("year") Integer year,
			@Param("split") String split,
			@Param("patch") String patch);
}
