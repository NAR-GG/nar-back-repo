package com.toy.nar.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.toy.nar.entity.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {
	Optional<Player> findByName(String name);
}
