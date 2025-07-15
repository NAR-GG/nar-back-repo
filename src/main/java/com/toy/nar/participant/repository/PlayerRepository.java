package com.toy.nar.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

import com.toy.nar.participant.entity.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	List<Player> findAllByNameInIgnoreCase(Collection<String> playerNames);
}
