package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerRiotAccountRepository extends JpaRepository<PlayerRiotAccount, Long> {

	Optional<PlayerRiotAccount> findByPlayerId(Long playerId);

	@Query("SELECT pra FROM PlayerRiotAccount pra " +
			"JOIN FETCH pra.player p " +
			"WHERE pra.enabled = true " +
			"AND pra.primaryAccount = true " +
			"AND pra.platform = :platform")
	List<PlayerRiotAccount> findTrackedAccountsByPlatform(@Param("platform") String platform);
}
