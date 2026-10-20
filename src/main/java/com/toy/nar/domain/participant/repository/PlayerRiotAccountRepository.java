package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayerRiotAccountRepository extends JpaRepository<PlayerRiotAccount, Long> {

	// 선수당 주계정은 하나다. 보조 계정(primary_account = false)이 있어도 이것만 고른다.
	Optional<PlayerRiotAccount> findByPlayerIdAndPrimaryAccountTrue(Long playerId);

	Optional<PlayerRiotAccount> findByPuuid(String puuid);

	// 플랫폼 무관 추적 대상 전체(KR·EUW1·NA1 ...). 현재 게임 폴링은 계정별 platform으로 라우팅한다.
	// 주계정과 보조 계정(월즈 부트캠프 계정 등)을 함께 돈다 — 선수 한 명이 여러 행일 수 있다.
	@Query("SELECT pra FROM PlayerRiotAccount pra " +
			"JOIN FETCH pra.player p " +
			"WHERE pra.enabled = true")
	List<PlayerRiotAccount> findAllTrackedAccounts();
}
