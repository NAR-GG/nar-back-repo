package com.toy.nar.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

import com.toy.nar.participant.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	List<Team> findAllByNameInIgnoreCase(Collection<String> teamNames);

}