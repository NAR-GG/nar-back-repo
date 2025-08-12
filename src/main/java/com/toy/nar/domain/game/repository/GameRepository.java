package com.toy.nar.domain.game.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.app.schedule.dto.ScheduleItemDto;
import com.toy.nar.domain.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.gameOriginId IN :gameIds")
	Set<String> findExistingGameIds(@Param("gameIds") Set<String> gameIds);

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.id IN :gameIds")
	Set<String> findGameOriginIdsByIds(@Param("gameIds") Set<Long> gameIds);

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

	@Query("SELECT DISTINCT g FROM Game g " +
		"JOIN FETCH g.participants p " +
		"JOIN FETCH p.team t " +
		"JOIN FETCH g.league l " +
		"WHERE g.scheduledGameStartTime >= :start AND g.scheduledGameStartTime < :end " +
		"ORDER BY g.scheduledGameStartTime ASC")
	List<Game> findGamesByScheduledDateWithDetails(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("SELECT g FROM Game g " +
		"LEFT JOIN FETCH g.league l " +
		"LEFT JOIN FETCH g.participants p " +
		"LEFT JOIN FETCH p.player " +
		"LEFT JOIN FETCH p.team " +
		"LEFT JOIN FETCH p.champion " +
		"LEFT JOIN FETCH p.stat " +
		"LEFT JOIN FETCH g.bans b " +
		"LEFT JOIN FETCH b.bannedChampion " +
		"WHERE g.id = :gameId")
	Optional<Game> findGameDetailsById(@Param("gameId") Long gameId);

	@Query("SELECT new com.toy.nar.app.schedule.dto.ScheduleItemDto(" +
		"   g.id, l.leagueName, l.seasonSplit, g.scheduledGameStartTime, t.name, p.isWin) " +
		"FROM Game g " +
		"JOIN g.participants p " +
		"JOIN p.team t " +
		"JOIN g.league l " +
		"WHERE g.scheduledGameStartTime >= :start AND g.scheduledGameStartTime < :end")
	List<ScheduleItemDto> findScheduleItemsByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
