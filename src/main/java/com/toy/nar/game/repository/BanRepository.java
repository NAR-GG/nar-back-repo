package com.toy.nar.game.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.game.entity.Ban;
import com.toy.nar.game.entity.Game;

public interface BanRepository extends JpaRepository<Ban, Long> {
	boolean existsByGame(Game game);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	int deleteByGameIdIn(Set<Long> gameIds);
}
