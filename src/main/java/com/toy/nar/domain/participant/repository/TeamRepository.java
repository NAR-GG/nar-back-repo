package com.toy.nar.domain.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import com.toy.nar.domain.participant.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	List<Team> findAllByNameInIgnoreCase(Collection<String> teamNames);

	@Modifying
	@Transactional
	@Query(value = "INSERT IGNORE INTO teams (team_name) VALUES (?1)", nativeQuery = true)
	void insertTeamIgnoreDuplicate(String teamName);

	// 🔥 여러 팀 INSERT IGNORE
	@Modifying
	@Transactional
	@Query(value = """
        INSERT IGNORE INTO teams (team_name) 
        VALUES (:#{#names})
        """, nativeQuery = true)
	void insertTeamsIgnoreDuplicates(@Param("names") List<String> teamNames);
}