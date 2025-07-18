package com.toy.nar.game.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.gameOriginId IN :gameIds")
	Set<String> findExistingGameIds(@Param("gameIds") Set<String> gameIds);

	@Query("SELECT g FROM Game g WHERE g.gameOriginId = :gameOriginId")
	Optional<Game> findByGameOriginId(@Param("gameOriginId") String gameOriginId);

	@Query("SELECT g.gameOriginId FROM Game g WHERE g.id IN :gameIds")
	Set<String> findGameOriginIdsByIds(@Param("gameIds") Set<Long> gameIds);
}
