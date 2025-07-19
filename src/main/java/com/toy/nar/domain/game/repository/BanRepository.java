package com.toy.nar.domain.game.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.toy.nar.domain.game.entity.Ban;
import com.toy.nar.domain.game.entity.Game;

public interface BanRepository extends JpaRepository<Ban, Long> {
	boolean existsByGame(Game game);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	int deleteByGameIdIn(Set<Long> gameIds);
}
