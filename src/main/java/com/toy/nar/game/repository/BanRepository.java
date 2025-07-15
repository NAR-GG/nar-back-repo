package com.toy.nar.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.game.entity.Ban;
import com.toy.nar.game.entity.Game;

public interface BanRepository extends JpaRepository<Ban, Long> {
	boolean existsByGame(Game game);
}
