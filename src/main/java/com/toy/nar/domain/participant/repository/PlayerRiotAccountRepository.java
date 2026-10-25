package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

	// 티어 컬럼만 고친다. 엔티티를 save(merge)하면 그 사이 모니터가 바꾼 라이브 상태 컬럼을 되돌린다(copyLiveStateFrom 참고).
	@Modifying
	@Transactional
	@Query("UPDATE PlayerRiotAccount pra SET pra.soloTier = :tier, pra.soloDivision = :division, pra.soloLp = :lp, " +
			"pra.soloTierCheckedAt = :checkedAt WHERE pra.id = :id")
	int updateSoloTier(@Param("id") Long id, @Param("tier") String tier, @Param("division") String division,
			@Param("lp") Integer lp, @Param("checkedAt") LocalDateTime checkedAt);

	// 기간 안에 끝난 솔랭 판과 그 판을 한 계정. 판 행엔 계정 id 가 없어 선수+플랫폼으로 잇는다.
	@Query("SELECT new com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository$EndedGame(a, g.gameEndedAt) " +
			"FROM PlayerRiotAccount a JOIN PlayerSoloRankGame g ON g.player = a.player AND g.platform = a.platform " +
			"WHERE a.enabled = true AND g.gameEndedAt > :from AND g.gameEndedAt <= :until")
	List<EndedGame> findEndedGames(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until);

	// 감지는 됐는데 결과(종료 시각)도 종료 알림도 없는 판과 그 계정 — [0]=계정, [1]=gameId.
	// 계정이 아직 그 판을 하고 있으면 뺀다. 생성자식은 JOIN FETCH 와 못 써서 Object[] 로 받는다.
	@Query("SELECT a, g.gameId FROM PlayerRiotAccount a JOIN FETCH a.player p " +
			"JOIN PlayerSoloRankGame g ON g.player = p AND g.platform = a.platform " +
			"WHERE a.enabled = true AND g.gameEndedAt IS NULL AND g.endNotifiedAt IS NULL AND g.detectedAt > :since " +
			"AND NOT (a.liveStatus = :live AND a.lastCheckedMatchId = g.gameId)")
	List<Object[]> findUnresolvedGames(@Param("since") LocalDateTime since, @Param("live") PlayerRiotAccountLiveStatus live);

	record EndedGame(PlayerRiotAccount account, LocalDateTime endedAt) {
	}
}
