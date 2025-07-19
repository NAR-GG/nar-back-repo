package com.toy.nar;


import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.toy.nar.domain.game.repository.GameParticipantRepository;

@Slf4j
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameDataIntegrityTest {

	@Autowired
	private GameParticipantRepository repository;

	@Test
	@DisplayName("불완전한 게임 데이터 검증")
	void shouldFindIncompleteGames() {
		// when
		List<Object[]> incompleteGames = repository.findIncompleteGames();

		// then
		incompleteGames.forEach(result -> {
			Long gameId = (Long) result[0];
			Number count = (Number) result[1];
			log.warn("Game ID: {} has {} participants (expected: 10)",
				gameId, count.intValue());
		});

		log.info("Total incomplete games found: {}", incompleteGames.size());
	}
}