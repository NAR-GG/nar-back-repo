package com.toy.nar.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

import com.toy.nar.entity.GameParticipant;

public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {
	List<GameParticipant> findByPosition(String upperCase);

	@Query("SELECT gp FROM GameParticipant gp WHERE gp.game.id IN :gameIds")
	List<GameParticipant> findByGameIds(Set<Long> gameIds);
}
