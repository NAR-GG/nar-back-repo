package com.toy.nar.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.toy.nar.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
	Optional<Game> findByGameOriginId(String gameOriginId);
}
