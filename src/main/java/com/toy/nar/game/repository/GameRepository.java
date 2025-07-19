package com.toy.nar.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.gameOriginId IN :gameIds")
	Set<String> findExistingGameIds(@Param("gameIds") Set<String> gameIds);

	@Query("SELECT g FROM Game g WHERE g.gameOriginId = :gameOriginId")
	Optional<Game> findByGameOriginId(@Param("gameOriginId") String gameOriginId);

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.id IN :gameIds")
	Set<String> findGameOriginIdsByIds(@Param("gameIds") Set<Long> gameIds);

	@Query("SELECT g.id FROM Game g WHERE g.league.leagueName = :leagueName")
	List<Long> findGameIdsByLeague_LeagueName(@Param("leagueName") String leagueName);

	@Query(value = """
        SELECT g.game_id
        FROM games g
        LEFT JOIN game_participants gp ON g.game_id = gp.game_id
        GROUP BY g.game_id
        HAVING COUNT(gp.participant_game_id) != 10
        """, nativeQuery = true)
	Set<Long> findIncompleteGameIds();

	@Query("SELECT DISTINCT g FROM Game g " +
		"LEFT JOIN FETCH g.participants p " +
		"LEFT JOIN FETCH g.bans b " +
		"LEFT JOIN FETCH p.player " +
		"LEFT JOIN FETCH p.team " +
		"LEFT JOIN FETCH p.champion " +
		"WHERE g.league.id = :leagueId")
	List<Game> findAllByLeagueIdWithDetails(@Param("leagueId") Long leagueId);
}
