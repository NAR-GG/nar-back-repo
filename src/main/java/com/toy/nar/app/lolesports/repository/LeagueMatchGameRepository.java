package com.toy.nar.app.lolesports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeagueMatchGameRepository extends JpaRepository<LeagueMatchGame, Long> {

	List<LeagueMatchGame> findByLeagueMatch_IdOrderByGameOrderAsc(String matchId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			DELETE FROM LeagueMatchGame g
			WHERE g.leagueMatch.id = :matchId
			""")
	int deleteAllByMatchId(@Param("matchId") String matchId);

	@Query("""
			SELECT g
			FROM LeagueMatchGame g
			JOIN FETCH g.leagueMatch m
			WHERE g.gameId = :gameId
			""")
	Optional<LeagueMatchGame> findWithMatchByGameId(@Param("gameId") String gameId);

	@Query("""
			SELECT g
			FROM LeagueMatchGame g
			WHERE g.leagueMatch.id IN :matchIds
			ORDER BY g.leagueMatch.id ASC, g.gameOrder ASC
			""")
	List<LeagueMatchGame> findAllByLeagueMatchIdsOrderByMatchAndGameOrder(@Param("matchIds") List<String> matchIds);
}
