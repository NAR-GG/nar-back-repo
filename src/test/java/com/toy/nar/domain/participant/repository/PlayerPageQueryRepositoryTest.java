package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.toy.nar.domain.participant.entity.Player;

@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(PlayerPageQueryRepository.class)
class PlayerPageQueryRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private PlayerPageQueryRepository repository;

	/** prod 선수 23명이 game_accounts NULL 이라 솔랭 탭이 NPE 로 500 이었다. */
	@Test
	void game_accounts_가_NULL_이면_빈_값이다() {
		Player player = em.persistFlushFind(Player.builder().name("Kael").build());

		assertThat(repository.gameAccountsJson(player.getId())).isEmpty();
	}
}
