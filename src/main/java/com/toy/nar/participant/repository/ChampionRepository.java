package com.toy.nar.participant.repository;

import com.toy.nar.participant.entity.Champion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

}
