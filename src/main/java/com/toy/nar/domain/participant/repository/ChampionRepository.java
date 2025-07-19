package com.toy.nar.domain.participant.repository;

import java.util.List;
import java.util.Optional;

import com.toy.nar.domain.participant.entity.Champion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionRepository extends JpaRepository<Champion, Long> {
	List<Champion> findAllByOrderByChampionNameKrAsc();

	Optional<Champion> findByChampionNameEn(String championNameEn);
}
