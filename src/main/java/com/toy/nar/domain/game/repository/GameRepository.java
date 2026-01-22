package com.toy.nar.domain.game.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.app.analysis.dto.PlayerStatsDto;
import com.toy.nar.app.analysis.dto.ChampionBanStatsDto;
import com.toy.nar.app.analysis.dto.ChampionStatsDto;
import com.toy.nar.app.schedule.dto.ScheduleItemDto;
import com.toy.nar.domain.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long>, GameRepositoryCustom {

	@Query("SELECT new com.toy.nar.app.analysis.dto.PlayerStatsDto(" +
			"   gp.team.name, gp.player.name, gp.player.imageUrl, COUNT(gp), " +
			"   (SUM(s.kills) + SUM(s.assists)) * 1.0 / (CASE WHEN SUM(s.deaths) = 0 THEN 1 ELSE SUM(s.deaths) END)) " +
			"FROM GameParticipant gp " +
			"JOIN gp.stat s " +
			"JOIN gp.game g " +
			"JOIN g.league l " +
			"WHERE g.patch = :patch AND l.leagueName = :leagueName " +
			"GROUP BY gp.player.name, gp.team.name, gp.player.imageUrl " +
			"ORDER BY (SUM(s.kills) + SUM(s.assists)) * 1.0 / (CASE WHEN SUM(s.deaths) = 0 THEN 1 ELSE SUM(s.deaths) END) DESC")
	List<PlayerStatsDto> findTopKdaPlayersByPatchAndLeague(@Param("patch") String patch,
			@Param("leagueName") String leagueName, Pageable pageable);

	@Query("SELECT new com.toy.nar.app.analysis.dto.PlayerStatsDto(" +
			"   gp.team.name, gp.player.name, gp.player.imageUrl, COUNT(gp), AVG(s.earnedGpm)) " +
			"FROM GameParticipant gp " +
			"JOIN gp.stat s " +
			"JOIN gp.game g " +
			"JOIN g.league l " +
			"WHERE g.patch = :patch AND l.leagueName = :leagueName " +
			"GROUP BY gp.player.name, gp.team.name, gp.player.imageUrl " +
			"ORDER BY AVG(s.earnedGpm) DESC")
	List<PlayerStatsDto> findTopGpmPlayersByPatchAndLeague(@Param("patch") String patch,
			@Param("leagueName") String leagueName, Pageable pageable);

	@Query("SELECT new com.toy.nar.app.analysis.dto.PlayerStatsDto(" +
			"   gp.team.name, gp.player.name, gp.player.imageUrl, COUNT(gp), AVG(s.dpm)) " +
			"FROM GameParticipant gp " +
			"JOIN gp.stat s " +
			"JOIN gp.game g " +
			"JOIN g.league l " +
			"WHERE g.patch = :patch AND l.leagueName = :leagueName " +
			"GROUP BY gp.player.name, gp.team.name, gp.player.imageUrl " +
			"ORDER BY AVG(s.dpm) DESC")
	List<PlayerStatsDto> findTopDpmPlayersByPatchAndLeague(@Param("patch") String patch,
			@Param("leagueName") String leagueName, Pageable pageable);

	@Query("SELECT MAX(g.patch) FROM Game g JOIN g.league l WHERE l.leagueName = :leagueName")
	String findLatestPatchByLeague(@Param("leagueName") String leagueName);

	@Query("SELECT l.seasonYear FROM Game g JOIN g.league l WHERE l.leagueName = :leagueName AND g.patch = :patch ORDER BY g.actualGameStartTime DESC")
	List<Integer> findYearsByLeagueAndPatch(@Param("leagueName") String leagueName, @Param("patch") String patch,
			Pageable pageable);

	@Query("SELECT new com.toy.nar.app.analysis.dto.ChampionStatsDto(" +
			"   c.championNameKr, c.championNameEn, COUNT(gp), SUM(CASE WHEN gp.isWin = true THEN 1 ELSE 0 END)) " +
			"FROM GameParticipant gp JOIN gp.champion c " +
			"WHERE gp.game.patch = :patch AND gp.game.league.leagueName = :leagueName " +
			"GROUP BY c.championNameKr, c.championNameEn " +
			"ORDER BY COUNT(gp) DESC, (SUM(CASE WHEN gp.isWin = true THEN 1 ELSE 0 END) * 1.0 / COUNT(gp)) DESC")
	List<ChampionStatsDto> findChampionStatsByPatchAndLeague(@Param("patch") String patch,
			@Param("leagueName") String leagueName, Pageable pageable);

	@Query("SELECT new com.toy.nar.app.analysis.dto.ChampionBanStatsDto(" +
			"   c.championNameKr, c.championNameEn, COUNT(b)) " +
			"FROM Ban b JOIN b.game g JOIN b.bannedChampion c " +
			"WHERE g.patch = :patch AND g.league.leagueName = :leagueName " +
			"GROUP BY c.championNameKr, c.championNameEn " +
			"ORDER BY COUNT(b) DESC")
	List<ChampionBanStatsDto> findChampionBanStatsByPatchAndLeague(@Param("patch") String patch,
			@Param("leagueName") String leagueName);

	@Query("SELECT new com.toy.nar.app.analysis.dto.ChampionStatsDto(" +
			"   c.championNameKr, c.championNameEn, COUNT(gp), SUM(CASE WHEN gp.isWin = true THEN 1 ELSE 0 END)) " +
			"FROM GameParticipant gp JOIN gp.champion c " +
			"WHERE gp.game.patch = :patch AND gp.game.league.leagueName = :leagueName AND c.championNameKr IN :championNames "
			+
			"GROUP BY c.championNameKr, c.championNameEn")
	List<ChampionStatsDto> findChampionStatsByNamesAndPatch(@Param("patch") String patch,
			@Param("leagueName") String leagueName, @Param("championNames") List<String> championNames);

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

	@Query("SELECT DISTINCT g FROM Game g " +
			"JOIN FETCH g.league l " +
			"JOIN g.participants p " +
			"WHERE p.team.id IN :teamIds " +
			"ORDER BY g.actualGameStartTime DESC")
	List<Game> findRecentGamesByTeamIds(@Param("teamIds") List<Long> teamIds, Pageable pageable);

	default List<Game> findRecentGamesByTeamIds(List<Long> teamIds, int limit) {
		return findRecentGamesByTeamIds(teamIds, org.springframework.data.domain.PageRequest.of(0, limit));
	}

	@Query("SELECT DISTINCT g FROM Game g " +
			"JOIN FETCH g.league l " +
			"WHERE EXISTS (SELECT 1 FROM GameParticipant p1 WHERE p1.game = g AND p1.team.id IN :team1Ids) " +
			"AND EXISTS (SELECT 1 FROM GameParticipant p2 WHERE p2.game = g AND p2.team.id IN :team2Ids) " +
			"ORDER BY g.actualGameStartTime DESC")
	List<Game> findRecentGamesByBothTeams(@Param("team1Ids") List<Long> team1Ids,
			@Param("team2Ids") List<Long> team2Ids, Pageable pageable);

	default List<Game> findRecentGamesByBothTeams(List<Long> team1Ids, List<Long> team2Ids, int limit) {
		return findRecentGamesByBothTeams(team1Ids, team2Ids, org.springframework.data.domain.PageRequest.of(0, limit));
	}

	@Query("SELECT COUNT(g) FROM Game g JOIN g.league l WHERE g.patch = :patch AND l.leagueName = :leagueName")
	long countByPatchAndLeague(@Param("patch") String patch, @Param("leagueName") String leagueName);
}