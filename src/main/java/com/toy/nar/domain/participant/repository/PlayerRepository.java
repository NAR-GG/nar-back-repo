package com.toy.nar.domain.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.toy.nar.domain.participant.entity.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	@Query("SELECT p FROM Player p WHERE p.name IN :names")
	List<Player> findAllByNameInIgnoreCase(@Param("names") Set<String> names);
}
