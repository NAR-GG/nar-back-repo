package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PlayerRepository extends JpaRepository<Player, Long> {
	Optional<Player> findByName(String name);

	Optional<Player> findByPlayerOriginId(String playerOriginId);

	List<Player> findAllByNameInIgnoreCase(Set<String> names);

	@Query("SELECT DISTINCT p FROM Player p " +
		"JOIN GameParticipant gp ON gp.player = p " +
		"JOIN gp.game g " +
		"JOIN g.league l " +
		"WHERE l.leagueName = :leagueName")
	List<Player> findPlayersByLeagueName(@Param("leagueName") String leagueName);

	@Query("""
			SELECT DISTINCT p
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND (:teamId IS NULL OR t.id = :teamId)
			ORDER BY p.name
			""")
	List<Player> findOnboardingPlayers(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId);

	@Query(
			value = """
					SELECT DISTINCT
						p.id AS playerId,
						p.name AS playerName,
						p.imageUrl AS playerImageUrl,
						p.role AS role,
						t.id AS teamId,
						t.code AS teamCode,
						t.name AS teamName,
						t.imageUrl AS teamImageUrl
					FROM GameParticipant gp
					JOIN gp.player p
					JOIN gp.team t
					JOIN gp.game g
					JOIN g.league l
					WHERE l.leagueName = :leagueName
					  AND l.seasonYear = :year
					  AND g.actualGameStartTime = (
						  SELECT MAX(g2.actualGameStartTime)
						  FROM GameParticipant gp2
						  JOIN gp2.game g2
						  JOIN g2.league l2
						  WHERE gp2.player = p
						    AND l2.leagueName = :leagueName
						    AND l2.seasonYear = :year
					  )
					  AND (:teamId IS NULL OR t.id = :teamId)
					  AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
					ORDER BY p.name
					""",
			countQuery = """
					SELECT COUNT(DISTINCT p.id)
					FROM GameParticipant gp
					JOIN gp.player p
					JOIN gp.team t
					JOIN gp.game g
					JOIN g.league l
					WHERE l.leagueName = :leagueName
					  AND l.seasonYear = :year
					  AND g.actualGameStartTime = (
						  SELECT MAX(g2.actualGameStartTime)
						  FROM GameParticipant gp2
						  JOIN gp2.game g2
						  JOIN g2.league l2
						  WHERE gp2.player = p
						    AND l2.leagueName = :leagueName
						    AND l2.seasonYear = :year
					  )
					  AND (:teamId IS NULL OR t.id = :teamId)
					  AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
					""")
	Page<LckPlayerOption> findLckPlayerOptions(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("teamId") Long teamId,
			@Param("query") String query,
			Pageable pageable);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id = :playerId
			ORDER BY t.name
			""")
	List<LckPlayerOption> findLckPlayerOption(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerId") Long playerId);

	@Query("""
			SELECT DISTINCT
				p.id AS playerId,
				p.name AS playerName,
				p.imageUrl AS playerImageUrl,
				p.role AS role,
				t.id AS teamId,
				t.code AS teamCode,
				t.name AS teamName,
				t.imageUrl AS teamImageUrl
			FROM GameParticipant gp
			JOIN gp.player p
			JOIN gp.team t
			JOIN gp.game g
			JOIN g.league l
			WHERE l.leagueName = :leagueName
			  AND l.seasonYear = :year
			  AND g.actualGameStartTime = (
				  SELECT MAX(g2.actualGameStartTime)
				  FROM GameParticipant gp2
				  JOIN gp2.game g2
				  JOIN g2.league l2
				  WHERE gp2.player = p
				    AND l2.leagueName = :leagueName
				    AND l2.seasonYear = :year
			  )
			  AND p.id IN :playerIds
			ORDER BY p.name, t.name
			""")
	List<LckPlayerOption> findLckPlayerOptionsByPlayerIds(
			@Param("leagueName") String leagueName,
			@Param("year") int year,
			@Param("playerIds") Set<Long> playerIds);

	interface LckPlayerOption {
		Long getPlayerId();

		String getPlayerName();

		String getPlayerImageUrl();

		String getRole();

		Long getTeamId();

		String getTeamCode();

		String getTeamName();

		String getTeamImageUrl();
	}
}
