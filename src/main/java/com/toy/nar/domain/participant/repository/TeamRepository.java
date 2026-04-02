package com.toy.nar.domain.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.toy.nar.domain.participant.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	@Query("SELECT t FROM Team t WHERE t.name IN :names")
	List<Team> findAllByNameInIgnoreCase(@Param("names") Set<String> names);

	@Query("SELECT t FROM Team t LEFT JOIN FETCH t.leagueTeams WHERE t.name IN :names")
	List<Team> findAllByNameInWithLeagueTeams(@Param("names") Set<String> names);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
	List<Team> findByNameContainingIgnoreCase(@Param("keyword") String keyword);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND t.code IS NOT NULL")
	List<Team> findByNameContainingIgnoreCaseAndCodeIsNotNull(@Param("keyword") String keyword);

	@Query("SELECT t FROM Team t WHERE LOWER(t.code) = LOWER(:code) AND t.code IS NOT NULL")
	List<Team> findByCodeIgnoreCase(@Param("code") String code);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) = LOWER(:name)")
	Optional<Team> findByNameIgnoreCase(@Param("name") String name);
}
