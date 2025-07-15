package com.toy.nar.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.entity.Ban;
import com.toy.nar.entity.Game;

public interface BanRepository extends JpaRepository<Ban, Long> {
	boolean existsByGame(Game game);
}
