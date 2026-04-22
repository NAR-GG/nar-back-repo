package com.toy.nar.domain.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.toy.nar.domain.game.entity.LeagueTeam;
import com.toy.nar.domain.participant.entity.Team;

@Repository
public interface LeagueTeamRepository extends JpaRepository<LeagueTeam, Long> {

	@Query("SELECT lt.team FROM LeagueTeam lt WHERE lt.league.id = :leagueId")
	List<Team> findTeamsByLeagueId(@Param("leagueId") Long leagueId);

	@Query("SELECT lt.team FROM LeagueTeam lt WHERE lt.league.leagueName = :leagueName AND lt.league.seasonYear = :seasonYear AND lt.league.seasonSplit = :seasonSplit")
	List<Team> findTeamsByLeagueParams(@Param("leagueName") String leagueName,
			@Param("seasonYear") Integer seasonYear,
			@Param("seasonSplit") String seasonSplit);

	@Query("""
			SELECT DISTINCT lt.team
			FROM LeagueTeam lt
			WHERE lt.league.leagueName = :leagueName
			  AND lt.league.seasonYear = (
			  	SELECT MAX(l.seasonYear)
			  	FROM League l
			  	WHERE l.leagueName = :leagueName
			  )
			ORDER BY lt.team.name
			""")
	List<Team> findLatestTeamsByLeagueName(@Param("leagueName") String leagueName);

	@Modifying
	int deleteByLeague_LeagueName(String leagueName);

	@Query("SELECT lt.league.id, lt.team.id FROM LeagueTeam lt")
	List<Object[]> findAllLeagueTeamPairs();
}
