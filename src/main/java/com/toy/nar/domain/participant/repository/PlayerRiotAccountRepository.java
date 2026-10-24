package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerRiotAccountRepository extends JpaRepository<PlayerRiotAccount, Long> {

	// 선수당 주계정은 하나다. 보조 계정(primary_account = false)이 있어도 이것만 고른다.
	Optional<PlayerRiotAccount> findByPlayerIdAndPrimaryAccountTrue(Long playerId);

	Optional<PlayerRiotAccount> findByPuuid(String puuid);

	// 솔랭 팀 백필 — 판에 들어온 선수들의 계정(puuid·플랫폼). 꺼진 계정도 옛 판을 했을 수 있어 다 본다.
	List<PlayerRiotAccount> findByPlayerIdIn(Collection<Long> playerIds);

	// 선수 페이지 계정 칩 — 주계정 먼저.
	List<PlayerRiotAccount> findByPlayerIdAndEnabledTrueOrderByPrimaryAccountDesc(Long playerId);

	// 보조 계정(월즈 부트캠프 계정 등)이 켜져 있는 선수 = 월즈 솔랭 명단.
	@Query("SELECT DISTINCT pra.player.id FROM PlayerRiotAccount pra " +
			"WHERE pra.enabled = true AND pra.primaryAccount = false")
	List<Long> findEventPlayerIds();

	List<PlayerRiotAccount> findByPlayerIdInAndLiveStatus(Collection<Long> playerIds, PlayerRiotAccountLiveStatus liveStatus);

	// 플랫폼 무관 추적 대상 전체(KR·EUW1·NA1 ...). 현재 게임 폴링은 계정별 platform으로 라우팅한다.
	// 주계정과 보조 계정(월즈 부트캠프 계정 등)을 함께 돈다 — 선수 한 명이 여러 행일 수 있다.
	@Query("SELECT pra FROM PlayerRiotAccount pra " +
			"JOIN FETCH pra.player p " +
			"WHERE pra.enabled = true")
	List<PlayerRiotAccount> findAllTrackedAccounts();
}
