package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(PlayerPageQueryRepository.class)
class PlayerPageQueryRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private PlayerPageQueryRepository repository;

	@Autowired
	private PlayerRiotAccountRepository accountRepository;

	/** prod 선수 23명이 game_accounts NULL 이라 솔랭 탭이 NPE 로 500 이었다. */
	@Test
	void game_accounts_가_NULL_이면_빈_값이다() {
		Player player = em.persistFlushFind(Player.builder().name("Kael").build());

		assertThat(repository.gameAccountsJson(player.getId())).isEmpty();
	}

	@Test
	void 솔랭_티어_갱신은_라이브_상태를_건드리지_않는다() {
		Player player = em.persist(Player.builder().name("Caps").build());
		PlayerRiotAccount account = em.persistAndFlush(PlayerRiotAccount.builder()
				.player(player).riotId("G2 Caps#1324").gameName("G2 Caps").tagLine("1324").platform("NA1")
				.puuid("puuid-caps").enabled(true).liveStatus(PlayerRiotAccountLiveStatus.IN_RANKED_SOLO).build());

		accountRepository.updateSoloTier(account.getId(), "GRANDMASTER", "I", 812, LocalDateTime.of(2026, 10, 25, 16, 40));
		em.clear();

		PlayerRiotAccount found = em.find(PlayerRiotAccount.class, account.getId());
		assertThat(found.getSoloTier()).isEqualTo("GRANDMASTER");
		assertThat(found.getSoloLp()).isEqualTo(812);
		assertThat(found.getLiveStatus()).isEqualTo(PlayerRiotAccountLiveStatus.IN_RANKED_SOLO);
	}
}
