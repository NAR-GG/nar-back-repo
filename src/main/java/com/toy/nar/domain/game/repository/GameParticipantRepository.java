package com.toy.nar.domain.game.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;
import com.toy.nar.domain.game.entity.GameParticipant;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {

	@Modifying
	@Query("DELETE FROM GameParticipant gp WHERE gp.game.id = :gameId")
	void deleteByGameId(@Param("gameId") Long gameId);

	@Query("SELECT gp FROM GameParticipant gp " +
		"JOIN FETCH gp.game g " +
		"JOIN FETCH gp.champion c " +
		"JOIN FETCH gp.team t " +
		"LEFT JOIN FETCH g.league l " +
		"WHERE gp.game.id IN (" +
		"    SELECT p.game.id FROM GameParticipant p " +
		"    JOIN p.champion c2 " +
		"    WHERE c2.championNameEn IN :championNames" +
		") " +
		"AND (:year IS NULL OR l.seasonYear = :year) " +
		"AND (:split IS NULL OR l.seasonSplit = :split) " +
		"AND (:leagueName IS NULL OR l.leagueName = :leagueName) " +
		"AND (:teamName IS NULL OR t.name = :teamName) " +
		"AND (:patch IS NULL OR g.patch = :patch)")
	List<GameParticipant> findFilteredParticipants(
		@Param("championNames") List<String> championNames,
		@Param("year") Integer year,
		@Param("split") String split,
		@Param("leagueName") String leagueName,
		@Param("teamName") String teamName,
		@Param("patch") String patch
	);

	@Query("SELECT gp FROM GameParticipant gp " +
		"JOIN FETCH gp.game g " +
		"JOIN FETCH gp.champion c " +
		"JOIN FETCH gp.team t " +
		"LEFT JOIN FETCH g.league l " +
		"WHERE gp.game.id IN (" +
		"    SELECT p.game.id FROM GameParticipant p " +
		"    JOIN p.champion c2 " +
		"    WHERE c2.championNameEn IN :championNames" +
		") " +
		"AND (:year IS NULL OR l.seasonYear = :year) " +
		"AND (:splits IS NULL OR l.seasonSplit IN :splits) " +
		"AND (:leagueNames IS NULL OR l.leagueName IN :leagueNames) " +
		"AND (:teamNames IS NULL OR t.name IN :teamNames) " +
		"AND (:patch IS NULL OR g.patch = :patch)")
	List<GameParticipant> findFilteredParticipantsMulti(
		@Param("championNames") List<String> championNames,
		@Param("year") Integer year,
		@Param("splits") List<String> splits,
		@Param("leagueNames") List<String> leagueNames,
		@Param("teamNames") List<String> teamNames,
		@Param("patch") String patch
	);

	@Query("SELECT COUNT(gp) FROM GameParticipant gp " +
		"JOIN gp.game g " +
		"JOIN gp.champion c " +
		"JOIN gp.team t " +
		"LEFT JOIN g.league l " +
		"WHERE gp.game.id IN (" +
		"    SELECT p.game.id FROM GameParticipant p " +
		"    JOIN p.champion c2 " +
		"    WHERE c2.championNameEn IN :championNames" +
		") " +
		"AND (:year IS NULL OR l.seasonYear = :year) " +
		"AND (:splits IS NULL OR l.seasonSplit IN :splits) " +
		"AND (:leagueNames IS NULL OR l.leagueName IN :leagueNames) " +
		"AND (:teamNames IS NULL OR t.name IN :teamNames) " +
		"AND (:patch IS NULL OR g.patch = :patch)")
	long countFilteredParticipantsMulti(
		@Param("championNames") List<String> championNames,
		@Param("year") Integer year,
		@Param("splits") List<String> splits,
		@Param("leagueNames") List<String> leagueNames,
		@Param("teamNames") List<String> teamNames,
		@Param("patch") String patch
	);

	@Query("SELECT gp FROM GameParticipant gp " +
		"JOIN FETCH gp.game g " +
		"JOIN FETCH gp.champion c " +
		"JOIN FETCH gp.team t " +
		"JOIN FETCH gp.player p " +
		"LEFT JOIN FETCH g.league l " +
		"WHERE g.id IN :gameIds " +
		"ORDER BY g.gameDate DESC, gp.position ASC")
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

	// 기본 참가자 조회 (년도, 패치만 적용)
	@Query("SELECT gp FROM GameParticipant gp " +
		"JOIN FETCH gp.game g " +
		"JOIN FETCH gp.champion c " +
		"JOIN FETCH gp.team t " +
		"LEFT JOIN FETCH g.league l " +
		"WHERE gp.game.id IN (" +
		"    SELECT p.game.id FROM GameParticipant p " +
		"    JOIN p.champion c2 " +
		"    WHERE c2.championNameEn IN :championNames" +
		") " +
		"AND (:year IS NULL OR l.seasonYear = :year) " +
		"AND (:patch IS NULL OR g.patch = :patch)")
	List<GameParticipant> findBaseParticipants(
		@Param("championNames") List<String> championNames,
		@Param("year") Integer year,
		@Param("patch") String patch
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	int deleteByGameIdIn(Set<Long> gameIds);

	@Query("SELECT DISTINCT gp.game.league.id, gp.team.id FROM GameParticipant gp")
	List<Object[]> findAllDistinctLeagueTeamPairs();
}
