package com.toy.nar.app.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class PlayerAdminServiceTest {

	@Mock
	private PlayerRepository playerRepository;
	@Mock
	private TeamRepository teamRepository;
	@InjectMocks
	private PlayerAdminService playerAdminService;

	@Test
	@DisplayName("LCK 출전 이력 없는 선수는 수정 거부")
	void rejectsNonLckPlayer() {
		Player player = Player.builder().name("lplOnly").build();
		when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(false);

		assertThatThrownBy(() -> playerAdminService.update(1L, "img.png", null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LCK");
	}

	@Test
	@DisplayName("이미지 수정 시 잠금, 팀 변경 시 currentTeam 교체")
	void updatesImageAndTeam() {
		Player player = Player.builder().name("Faker").build();
		Team team = Team.builder().name("Gen.G").build();
		when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);
		when(teamRepository.findById(2L)).thenReturn(Optional.of(team));

		Player updated = playerAdminService.update(1L, "manual.png", null, 2L);

		assertThat(updated.getImageUrl()).isEqualTo("manual.png");
		assertThat(updated.isImageLocked()).isTrue();
		assertThat(updated.getCurrentTeam()).isEqualTo(team);
	}

	@Test
	@DisplayName("unlockImage=true면 잠금 해제(이미지 값은 유지)")
	void unlocksImage() {
		Player player = Player.builder().name("Faker").build();
		player.overrideImage("manual.png");
		when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);

		Player updated = playerAdminService.update(1L, null, true, null);

		assertThat(updated.isImageLocked()).isFalse();
		assertThat(updated.getImageUrl()).isEqualTo("manual.png");
	}
}
