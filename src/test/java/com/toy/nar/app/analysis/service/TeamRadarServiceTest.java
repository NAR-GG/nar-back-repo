package com.toy.nar.app.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.analysis.dto.TeamRadarResponse;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

@ExtendWith(MockitoExtension.class)
class TeamRadarServiceTest {

	@Mock
	private GameTeamStatRepository gameTeamStatRepository;

	@Mock
	private GameParticipantRepository gameParticipantRepository;

	@InjectMocks
	private TeamRadarService teamRadarService;

	@Test
	@DisplayName("ALL 필터에서는 gold raw와 score를 함께 계산하고 side는 null로 전달한다")
	void getTeamRadarStats_allSideComputesGoldScores() {
		League league = league(1L, "LCK", 2026, "Round 1-2");
		Team gen = team(23L, "GEN");
		Team t1 = team(1L, "T1");
		Game game1 = game(101L, league, "14.1", LocalDateTime.of(2026, 1, 10, 17, 0), 1, 0.95);
		Game game2 = game(102L, league, "14.1", LocalDateTime.of(2026, 1, 12, 17, 0), 2, 0.90);

		List<GameTeamStat> allStats = List.of(
				teamStat(game1, gen, 1, 20, true, true, true, true, true, true, 3, 1, 8, 3, 6, 1, 0.12),
				teamStat(game2, gen, 1, 18, true, false, true, false, true, true, 2, 1, 7, 4, 4, 2, 0.10),
				teamStat(game1, t1, 0, 10, false, false, false, false, false, false, 1, 3, 3, 8, 1, 6, -0.12),
				teamStat(game2, t1, 0, 12, false, true, false, true, false, false, 1, 2, 4, 7, 2, 4, -0.10));

		List<GameParticipant> allParticipants = new ArrayList<>();
		allParticipants.addAll(participants(game1, gen, "Blue", 150, 250, 350, 450));
		allParticipants.addAll(participants(game2, gen, "Red", 150, 250, 350, 450));
		allParticipants.addAll(participants(game1, t1, "Red", -150, -250, -350, -450));
		allParticipants.addAll(participants(game2, t1, "Blue", -150, -250, -350, -450));

		when(gameTeamStatRepository.findByFilter("LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(allStats);
		when(gameParticipantRepository.findByFilterWithStats("LCK", 2026, "Round 1-2", "14.1", null))
				.thenReturn(allParticipants);

		TeamRadarResponse response = teamRadarService.getTeamRadarStats(23L, "lck", 2026, "Round 1-2", "14.1", "ALL");

		assertThat(response.getStats().getTeamId()).isEqualTo(23L);
		assertThat(response.getStats().getGamesPlayed()).isEqualTo(2);
		assertThat(response.getStats().getGoldDiffAt10()).isEqualTo(100.0);
		assertThat(response.getStats().getGoldDiffAt15()).isEqualTo(100.0);
		assertThat(response.getStats().getGoldDiffAt10Score()).isEqualTo(100.0);
		assertThat(response.getLeagueAverage().getTeamName()).isEqualTo("LCK Average");
		assertThat(response.getLeagueAverage().getGamesPlayed()).isEqualTo(2);
		assertThat(response.getLeagueAverage().getGoldDiffAt10()).isEqualTo(50.0);
		assertThat(response.getLeagueAverage().getGoldDiffAt10Score()).isEqualTo(50.0);

		verify(gameTeamStatRepository).findByFilter("LCK", 2026, "Round 1-2", "14.1", null);
		verify(gameParticipantRepository).findByFilterWithStats("LCK", 2026, "Round 1-2", "14.1", null);
	}

	@Test
	@DisplayName("side 필터를 사용하면 대문자로 정규화되고 편차가 0이면 gold score는 50이다")
	void getTeamRadarStats_normalizesSideAndHandlesFlatCohort() {
		League league = league(1L, "LCK", 2026, "Round 1-2");
		Team gen = team(23L, "GEN");
		Game game = game(201L, league, "14.1", LocalDateTime.of(2026, 2, 1, 17, 0), 1, 1.02);

		List<GameTeamStat> allStats = List.of(
				teamStat(game, gen, 1, 16, true, true, true, true, true, true, 2, 0, 9, 2, 5, 0, 0.11));
		List<GameParticipant> allParticipants = participants(game, gen, "Blue", -40, 20, 60, 120);

		when(gameTeamStatRepository.findByFilter("LCK", 2026, "Round 1-2", "14.1", "BLUE"))
				.thenReturn(allStats);
		when(gameParticipantRepository.findByFilterWithStats("LCK", 2026, "Round 1-2", "14.1", "BLUE"))
				.thenReturn(allParticipants);

		TeamRadarResponse response = teamRadarService.getTeamRadarStats(23L, "LCK", 2026, "Round 1-2", "14.1", "blue");

		assertThat(response.getStats().getGoldDiffAt10()).isEqualTo(50.0);
		assertThat(response.getStats().getGoldDiffAt10Score()).isEqualTo(50.0);
		assertThat(response.getLeagueAverage().getGoldDiffAt10()).isEqualTo(50.0);
		assertThat(response.getLeagueAverage().getGoldDiffAt10Score()).isEqualTo(50.0);

		verify(gameTeamStatRepository).findByFilter("LCK", 2026, "Round 1-2", "14.1", "BLUE");
		verify(gameParticipantRepository).findByFilterWithStats("LCK", 2026, "Round 1-2", "14.1", "BLUE");
	}

	@Test
	@DisplayName("필터에 맞는 경기 데이터가 없으면 예외를 던진다")
	void getTeamRadarStats_noData() {
		when(gameTeamStatRepository.findByFilter("LCK", 2026, null, null, null))
				.thenReturn(List.of());

		assertThatThrownBy(() -> teamRadarService.getTeamRadarStats(23L, "LCK", 2026, null, null, "ALL"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("No games found for league LCK in year 2026");
	}

	private Team team(Long id, String name) {
		Team team = Team.builder()
				.name(name)
				.code(name)
				.imageUrl(name + ".png")
				.build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}

	private League league(Long id, String leagueName, int year, String split) {
		League league = League.builder()
				.leagueName(leagueName)
				.seasonYear(year)
				.seasonSplit(split)
				.isPlayoffs(false)
				.build();
		ReflectionTestUtils.setField(league, "id", id);
		return league;
	}

	private Game game(Long id, League league, String patch, LocalDateTime startTime, int gameNumber, double ckpm) {
		Game game = Game.builder()
				.league(league)
				.actualGameStartTime(startTime)
				.gameNumber(gameNumber)
				.patch(patch)
				.gameLengthSeconds(1800)
				.ckpm(ckpm)
				.build();
		ReflectionTestUtils.setField(game, "id", id);
		return game;
	}

	private GameTeamStat teamStat(
			Game game,
			Team team,
			int result,
			int teamKills,
			boolean firstBlood,
			boolean firstTower,
			boolean firstThreeTower,
			boolean firstHerald,
			boolean firstDragon,
			boolean firstBaron,
			int dragons,
			int oppDragons,
			int towers,
			int oppTowers,
			int voidGrubs,
			int oppVoidGrubs,
			double gspd) {
		return GameTeamStat.builder()
				.game(game)
				.team(team)
				.result(result)
				.teamKills(teamKills)
				.teamDeaths(0)
				.isFirstBlood(firstBlood)
				.gspd(gspd)
				.isFirstDragon(firstDragon)
				.dragons(dragons)
				.oppDragons(oppDragons)
				.isFirstHerald(firstHerald)
				.heralds(firstHerald ? 1 : 0)
				.oppHeralds(firstHerald ? 0 : 1)
				.voidGrubs(voidGrubs)
				.oppVoidGrubs(oppVoidGrubs)
				.isFirstBaron(firstBaron)
				.barons(firstBaron ? 1 : 0)
				.oppBarons(firstBaron ? 0 : 1)
				.isFirstTower(firstTower)
				.towers(towers)
				.oppTowers(oppTowers)
				.isFirstToThreeTowers(firstThreeTower)
				.build();
	}

	private List<GameParticipant> participants(
			Game game,
			Team team,
			String side,
			int goldDiffAt10,
			int goldDiffAt15,
			int goldDiffAt20,
			int goldDiffAt25) {
		List<GameParticipant> participants = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Player player = Player.builder()
					.name(team.getName() + "-player-" + i)
					.imageUrl(team.getName() + "-player-" + i + ".png")
					.build();
			Champion champion = Champion.builder()
					.championNameKr("챔피언" + i)
					.championNameEn("Champion" + i)
					.imageUrl("champion-" + i + ".png")
					.loadingImageUrl("champion-" + i + "-loading.png")
					.build();
			GameParticipant participant = GameParticipant.builder()
					.game(game)
					.player(player)
					.team(team)
					.side(side)
					.position("top")
					.champion(champion)
					.isWin(true)
					.build();
			GamePlayerStat stat = GamePlayerStat.builder()
					.gameParticipant(participant)
					.goldDiffAt10(goldDiffAt10)
					.goldDiffAt15(goldDiffAt15)
					.goldDiffAt20(goldDiffAt20)
					.goldDiffAt25(goldDiffAt25)
					.build();
			participant.setStat(stat);
			participants.add(participant);
		}
		return participants;
	}
}
