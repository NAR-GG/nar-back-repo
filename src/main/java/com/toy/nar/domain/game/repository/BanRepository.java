package com.toy.nar.domain.game.repository;

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
}
