package com.toy.nar.app.lolesports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeagueMatchGameRepository extends JpaRepository<LeagueMatchGame, Long> {

	interface MappedGameRow {
		String getMatchId();

		Integer getGameOrder();

		Long getInternalGameId();
	}

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

	@Query(value = """
			SELECT lmg.match_id AS matchId,
			       lmg.game_order AS gameOrder,
			       gei.game_id AS internalGameId
			FROM league_match_game lmg
			LEFT JOIN game_external_identity gei
			  ON gei.source = :source
			 AND gei.external_game_id = lmg.game_id
			WHERE lmg.match_id = :matchId
			ORDER BY lmg.game_order ASC
			""", nativeQuery = true)
	List<MappedGameRow> findMappedGameRowsByMatchId(@Param("matchId") String matchId, @Param("source") String source);

	@Query(value = """
			SELECT lmg.match_id AS matchId,
			       lmg.game_order AS gameOrder,
			       gei.game_id AS internalGameId
			FROM league_match_game lmg
			LEFT JOIN game_external_identity gei
			  ON gei.source = :source
			 AND gei.external_game_id = lmg.game_id
			WHERE lmg.match_id IN :matchIds
			ORDER BY lmg.match_id ASC, lmg.game_order ASC
			""", nativeQuery = true)
	List<MappedGameRow> findMappedGameRowsByMatchIds(@Param("matchIds") List<String> matchIds, @Param("source") String source);
}
