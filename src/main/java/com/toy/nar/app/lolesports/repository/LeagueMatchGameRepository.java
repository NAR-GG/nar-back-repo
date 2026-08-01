package com.toy.nar.app.lolesports.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeagueMatchGameRepository extends JpaRepository<LeagueMatchGame, Long> {

	interface MappedGameRow {
		String getMatchId();

		Integer getGameOrder();

		String getExternalGameId();

		Long getInternalGameId();
	}

	/** 세트 목록용. 매핑 정보에 세트 승리 팀을 더한 행. */
	interface MappedGameWinnerRow extends MappedGameRow {

		/** 세트 승리 팀의 lolesports 팀 id. 기록 미적재·매핑 없음이면 null. */
		String getWinnerExternalTeamId();

		/** 세트 승리 팀의 내부 팀 코드. 외부 id 매핑이 없을 때의 폴백. */
		String getWinnerTeamCode();
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
			       lmg.game_id AS externalGameId,
			       gei.game_id AS internalGameId
			FROM league_match_game lmg
			LEFT JOIN game_external_identity gei
			  ON gei.source = :source
			 AND gei.external_game_id = lmg.game_id
			WHERE lmg.match_id = :matchId
			ORDER BY lmg.game_order ASC
			""", nativeQuery = true)
	List<MappedGameRow> findMappedGameRowsByMatchId(@Param("matchId") String matchId, @Param("source") String source);

	/**
	 * 매치 상세(세트 목록)용. 매핑 + 세트 승리 팀을 한 번에 읽는다.
	 *
	 * <p>세트 승자는 적재된 경기 기록(game_participants.is_win)에만 있다 — 업스트림
	 * getEventDetails 의 games[].teams 는 side 만 주고 세트별 승패를 주지 않는다(실측).
	 * 팀 식별은 lolesports 외부 팀 id 를 우선한다. 내부 팀 코드는 리그 표기와 다를 수 있다.</p>
	 *
	 * <p>winner 파생 테이블은 반드시 이 매치의 게임으로 한정해야 한다 — 한정이 없으면
	 * game_participants 전체(수십만 행)를 호출마다 머티리얼라이즈해 프로드에서 쿼리당 1초가 걸렸다.</p>
	 */
	@Query(value = """
			SELECT lmg.match_id AS matchId,
			       lmg.game_order AS gameOrder,
			       lmg.game_id AS externalGameId,
			       gei.game_id AS internalGameId,
			       winner.external_team_id AS winnerExternalTeamId,
			       winner.team_code AS winnerTeamCode
			FROM league_match_game lmg
			LEFT JOIN game_external_identity gei
			  ON gei.source = :source
			 AND gei.external_game_id = lmg.game_id
			LEFT JOIN (
			    SELECT DISTINCT gp.game_id AS game_id,
			           t.team_code AS team_code,
			           tei.external_team_id AS external_team_id
			    FROM game_participants gp
			    JOIN teams t ON t.team_id = gp.team_id
			    LEFT JOIN team_external_identity tei
			      ON tei.team_id = t.team_id
			     AND tei.source = :source
			    WHERE gp.is_win = TRUE
			      AND gp.game_id IN (
			          SELECT gei2.game_id
			          FROM league_match_game lmg2
			          JOIN game_external_identity gei2
			            ON gei2.source = :source
			           AND gei2.external_game_id = lmg2.game_id
			          WHERE lmg2.match_id = :matchId
			      )
			) winner ON winner.game_id = gei.game_id
			WHERE lmg.match_id = :matchId
			ORDER BY lmg.game_order ASC
			""", nativeQuery = true)
	List<MappedGameWinnerRow> findMappedGameWinnerRowsByMatchId(
			@Param("matchId") String matchId, @Param("source") String source);

	@Query(value = """
			SELECT lmg.match_id AS matchId,
			       lmg.game_order AS gameOrder,
			       lmg.game_id AS externalGameId,
			       gei.game_id AS internalGameId
			FROM league_match_game lmg
			LEFT JOIN game_external_identity gei
			  ON gei.source = :source
			 AND gei.external_game_id = lmg.game_id
			WHERE lmg.match_id IN :matchIds
			ORDER BY lmg.match_id ASC, lmg.game_order ASC
			""", nativeQuery = true)
	List<MappedGameRow> findMappedGameRowsByMatchIds(@Param("matchIds") List<String> matchIds, @Param("source") String source);

	@Query("""
			SELECT g
			FROM LeagueMatchGame g
			JOIN FETCH g.leagueMatch
			WHERE g.gameId IN :gameIds
			""")
	List<LeagueMatchGame> findAllWithMatchByGameIdIn(@Param("gameIds") Collection<String> gameIds);
}
