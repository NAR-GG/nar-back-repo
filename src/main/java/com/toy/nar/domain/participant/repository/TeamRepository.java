package com.toy.nar.domain.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.toy.nar.domain.participant.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	@Query("SELECT t FROM Team t WHERE t.name IN :names")
	List<Team> findAllByNameInIgnoreCase(@Param("names") Set<String> names);

}