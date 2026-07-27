package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayerRiotAccountRepository extends JpaRepository<PlayerRiotAccount, Long> {

	Optional<PlayerRiotAccount> findByPlayerId(Long playerId);

	// 플랫폼 무관 추적 대상 전체(KR·EUW1·NA1 ...). 현재 게임 폴링은 계정별 platform으로 라우팅한다.
	@Query("SELECT pra FROM PlayerRiotAccount pra " +
			"JOIN FETCH pra.player p " +
			"WHERE pra.enabled = true " +
			"AND pra.primaryAccount = true")
	List<PlayerRiotAccount> findAllTrackedAccounts();
}
