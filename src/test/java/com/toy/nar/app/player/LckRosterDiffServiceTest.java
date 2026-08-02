package com.toy.nar.app.player;

import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LckRosterDiffServiceTest {

	@Mock
	private LolesportsPlayerImageClient lolesportsPlayerImageClient;

	@Mock
	private PlayerRepository playerRepository;

	@InjectMocks
	private LckRosterDiffService lckRosterDiffService;

	private Player player(String name, String teamCode) {
		Player player = Player.builder().name(name).build();
		player.changeCurrentTeam(Team.builder().name(teamCode + " team").code(teamCode).build());
		return player;
	}

	@Test
	void 로스터_팀이_다른_선수만_반환한다() {
		when(lolesportsPlayerImageClient.fetchLckFirstTeamRosters())
				.thenReturn(Map.of("aiming", "KRX", "jiwoo", "KT", "faker", "T1"));
		when(playerRepository.findByCurrentTeamCodeIn(LckTeamCatalog.TEAM_CODES))
				.thenReturn(List.of(player("Aiming", "KT"), player("Jiwoo", "KRX"), player("Faker", "T1")));

		assertThat(lckRosterDiffService.detect())
				.extracting(
						LckRosterDiffService.RosterDiff::playerName,
						LckRosterDiffService.RosterDiff::currentTeamCode,
						LckRosterDiffService.RosterDiff::rosterTeamCode)
				.containsExactlyInAnyOrder(
						tuple("Aiming", "KT", "KRX"),
						tuple("Jiwoo", "KRX", "KT"));
	}

	@Test
	void 로스터에_없는_선수는_건너뛴다() {
		// 은퇴·해외 이적은 "팀 이동"이 아니라서 알림 대상이 아니다.
		when(lolesportsPlayerImageClient.fetchLckFirstTeamRosters()).thenReturn(Map.of("faker", "T1"));
		when(playerRepository.findByCurrentTeamCodeIn(LckTeamCatalog.TEAM_CODES))
				.thenReturn(List.of(player("Gone", "KT")));

		assertThat(lckRosterDiffService.detect()).isEmpty();
	}

	@Test
	void 대소문자와_공백이_달라도_같은_팀이면_알리지_않는다() {
		when(lolesportsPlayerImageClient.fetchLckFirstTeamRosters()).thenReturn(Map.of("aiming", "KRX"));
		when(playerRepository.findByCurrentTeamCodeIn(LckTeamCatalog.TEAM_CODES))
				.thenReturn(List.of(player(" Aiming ", "krx")));

		assertThat(lckRosterDiffService.detect()).isEmpty();
	}

	@Test
	void getTeams_응답이_비면_DB를_읽지_않고_빈_목록을_준다() {
		// API 장애 때 "전원 이적"으로 오인해 알림 폭탄이 터지면 안 된다.
		when(lolesportsPlayerImageClient.fetchLckFirstTeamRosters()).thenReturn(Map.of());

		assertThat(lckRosterDiffService.detect()).isEmpty();
		verify(playerRepository, never()).findByCurrentTeamCodeIn(any());
	}
}
