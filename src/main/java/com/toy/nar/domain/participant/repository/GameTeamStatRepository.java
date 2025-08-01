package com.toy.nar.domain.participant.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.participant.entity.GameTeamStat;

public interface GameTeamStatRepository extends JpaRepository<GameTeamStat, Long> {

	Collection<GameTeamStat> findByGameId(Long id);

}
