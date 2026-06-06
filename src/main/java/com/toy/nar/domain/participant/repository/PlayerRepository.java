package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
