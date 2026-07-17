package com.toy.nar.app.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.api.admin.BackofficeController;
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
	@Mock
	private ObjectMapper objectMapper;
	@InjectMocks
	private PlayerAdminService playerAdminService;

	@Test
	@DisplayName("LCK 출전 이력 없는 선수는 수정 거부")
	void rejectsNonLckPlayer() {
		Player player = Player.builder().name("lplOnly").build();
		when(playerRepository.findWithCurrentTeamById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(false);

		assertThatThrownBy(() -> playerAdminService.update(1L, "img.png", null, null, null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LCK");
	}

	@Test
	@DisplayName("이미지 수정 시 잠금, 팀 변경 시 currentTeam 교체")
	void updatesImageAndTeam() {
		Player player = Player.builder().name("Faker").build();
		Team team = Team.builder().name("Gen.G").build();
		when(playerRepository.findWithCurrentTeamById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);
		when(teamRepository.findById(2L)).thenReturn(Optional.of(team));

		Player updated = playerAdminService.update(1L, "manual.png", null, 2L, null, null);

		assertThat(updated.getImageUrl()).isEqualTo("manual.png");
		assertThat(updated.isImageLocked()).isTrue();
		assertThat(updated.getCurrentTeam()).isEqualTo(team);
	}

	@Test
	@DisplayName("unlockImage=true면 잠금 해제(이미지 값은 유지)")
	void unlocksImage() {
		Player player = Player.builder().name("Faker").build();
		player.overrideImage("manual.png");
		when(playerRepository.findWithCurrentTeamById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);

		Player updated = playerAdminService.update(1L, null, true, null, null, null);

		assertThat(updated.isImageLocked()).isFalse();
		assertThat(updated.getImageUrl()).isEqualTo("manual.png");
	}

	@Test
	@DisplayName("gameAccounts 수정 시 JSON 직렬화·잠금, riotId 형식(#) 검증")
	void updatesGameAccounts() throws Exception {
		Player player = Player.builder().name("Faker").build();
		when(playerRepository.findWithCurrentTeamById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);
		when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
				.thenReturn("[{\"region\":\"KR\",\"riotId\":\"Hide on bush#KR1\",\"tier\":null}]");

		Player updated = playerAdminService.update(1L, null, null, null, null,
				List.of(new BackofficeController.GameAccountEntry("KR", "Hide on bush#KR1", null)));

		assertThat(updated.getGameAccounts()).contains("Hide on bush#KR1");
		assertThat(updated.isGameAccountsLocked()).isTrue();
	}

	@Test
	@DisplayName("riotId에 #이 없으면 IllegalArgumentException")
	void rejectsInvalidRiotId() {
		Player player = Player.builder().name("Faker").build();
		when(playerRepository.findWithCurrentTeamById(1L)).thenReturn(Optional.of(player));
		when(playerRepository.hasLeagueParticipation(1L, "LCK")).thenReturn(true);

		assertThatThrownBy(() -> playerAdminService.update(1L, null, null, null, null,
				List.of(new BackofficeController.GameAccountEntry("KR", "NoTagLine", null))))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
