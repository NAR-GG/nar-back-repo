package com.toy.nar.app.riot;

import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoloRankGameHistoryRecorderTest {

	@Mock
	private PlayerSoloRankGameRepository repository;

	@InjectMocks
	private SoloRankGameHistoryRecorder recorder;

	private Player player(Long id) {
		Player player = Player.builder().name("Faker").build();
		ReflectionTestUtils.setField(player, "id", id);
		return player;
	}

	@Test
	void savesNewGame() {
		when(repository.existsByPlayer_IdAndGameId(7L, "222")).thenReturn(false);

		recorder.record(player(7L), "222", null, LocalDateTime.now());

		verify(repository).save(any(PlayerSoloRankGame.class));
	}

	@Test
	void skipsDuplicate() {
		when(repository.existsByPlayer_IdAndGameId(7L, "222")).thenReturn(true);

		recorder.record(player(7L), "222", null, LocalDateTime.now());

		verify(repository, never()).save(any());
	}

	@Test
	void ignoresBlankInput() {
		recorder.record(null, "222", null, LocalDateTime.now());
		recorder.record(player(7L), "", null, LocalDateTime.now());

		verify(repository, never()).save(any());
	}
}
