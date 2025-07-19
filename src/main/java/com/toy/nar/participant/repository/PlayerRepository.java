package com.toy.nar.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import com.toy.nar.participant.entity.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

	List<Player> findAllByNameInIgnoreCase(Collection<String> playerNames);

	@Modifying
	@Transactional
	@Query(value = "INSERT IGNORE INTO players (player_name) VALUES (?1)", nativeQuery = true)
	void insertPlayerIgnoreDuplicate(String playerName);

	// 🔥 여러 플레이어 INSERT IGNORE (더 효율적)
	@Modifying
	@Transactional
	@Query(value = """
        INSERT IGNORE INTO players (player_name) 
        VALUES (:#{#names})
        """, nativeQuery = true)
	void insertPlayersIgnoreDuplicates(@Param("names") List<String> playerNames);
}
