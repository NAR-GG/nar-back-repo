package com.toy.nar.app.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;

@ExtendWith(MockitoExtension.class)
class ScheduleFinderTest {

	@InjectMocks
	private ScheduleFinder scheduleFinder;

	@Mock
	private GameRepository gameRepository;

	@Mock
	private com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository;

	@Mock
	private LeagueMatchGameRepository leagueMatchGameRepository;

	@Mock
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("일정 목록 isSynced는 match-game 매핑이 모두 존재하면 true다")
	void createScheduleResponseDtoUsesMappedGameStatusFirst() {
		LocalDate targetDate = LocalDate.of(2026, 3, 4);
		LeagueMatch match = LeagueMatch.builder()
				.id("115654899804988513")
				.leagueName("LPL")
				.matchTitle("플레이오프 | BLG vs JDG")
				.matchDate(LocalDateTime.of(2026, 3, 4, 0, 0))
				.state("completed")
				.blueTeamName("BILIBILI GAMING")
				.blueTeamCode("BLG")
				.blueScore(3)
				.redTeamName("Beijing JDG Esports")
				.redTeamCode("JDG")
				.redScore(2)
				.build();

		given(leagueMatchRepository.findByDateRange(
				LocalDateTime.of(2026, 3, 4, 0, 0),
				LocalDateTime.of(2026, 3, 4, 23, 59, 59)))
				.willReturn(List.of(match));
		given(gameRepository.findAllByActualGameStartTimeBetween(
				LocalDateTime.of(2026, 3, 3, 12, 0),
				LocalDateTime.of(2026, 3, 5, 11, 59, 59)))
				.willReturn(List.of());
		given(leagueMatchGameRepository.findMappedGameRowsByMatchIds(List.of(match.getId()), "LOLESPORTS"))
				.willReturn(List.of(
						mappedRow(match.getId(), 1, 11933L),
						mappedRow(match.getId(), 2, 11934L),
						mappedRow(match.getId(), 3, 11930L),
						mappedRow(match.getId(), 4, 11931L),
						mappedRow(match.getId(), 5, 11932L)));

		ScheduleResponseDto response = scheduleFinder.createScheduleResponseDto(targetDate);

		assertThat(response.matches()).singleElement()
				.extracting(matchSummary -> matchSummary.isSynced())
				.isEqualTo(true);
	}

	@Test
	@DisplayName("매핑 데이터가 없으면 기존 팀명/날짜 fallback으로 isSynced를 판단한다")
	void createScheduleResponseDtoFallsBackWhenNoMappedRowsExist() {
		LocalDate targetDate = LocalDate.of(2026, 3, 4);
		LeagueMatch match = LeagueMatch.builder()
				.id("legacy-match")
				.leagueName("LCS")
				.matchTitle("플레이오프 | C9 vs TL")
				.matchDate(LocalDateTime.of(2026, 3, 4, 0, 0))
				.state("completed")
				.blueTeamName("Cloud9 Kia")
				.blueTeamCode("C9")
				.blueScore(3)
				.redTeamName("Team Liquid Alienware")
				.redTeamCode("TL")
				.redScore(2)
				.build();
		Game mappedGame = createGame(
				1L,
				LocalDateTime.of(2026, 3, 4, 0, 12),
				"Cloud9",
				"Team Liquid");

		given(leagueMatchRepository.findByDateRange(
				LocalDateTime.of(2026, 3, 4, 0, 0),
				LocalDateTime.of(2026, 3, 4, 23, 59, 59)))
				.willReturn(List.of(match));
		given(gameRepository.findAllByActualGameStartTimeBetween(
				LocalDateTime.of(2026, 3, 3, 12, 0),
				LocalDateTime.of(2026, 3, 5, 11, 59, 59)))
				.willReturn(List.of(mappedGame));
		given(leagueMatchGameRepository.findMappedGameRowsByMatchIds(List.of(match.getId()), "LOLESPORTS"))
				.willReturn(List.of());

		ScheduleResponseDto response = scheduleFinder.createScheduleResponseDto(targetDate);

		assertThat(response.matches()).singleElement()
				.extracting(matchSummary -> matchSummary.isSynced())
				.isEqualTo(true);
	}

	private LeagueMatchGameRepository.MappedGameRow mappedRow(String matchId, Integer gameOrder, Long internalGameId) {
		return new LeagueMatchGameRepository.MappedGameRow() {
			@Override
			public String getMatchId() {
				return matchId;
			}

			@Override
			public Integer getGameOrder() {
				return gameOrder;
			}

			@Override
			public Long getInternalGameId() {
				return internalGameId;
			}
		};
	}

	private Game createGame(Long gameId, LocalDateTime actualStartTime, String blueTeamName, String redTeamName) {
		League league = League.builder()
				.leagueName("LPL")
				.seasonSplit("Spring")
				.seasonYear(2026)
				.isPlayoffs(true)
				.build();
		Game game = Game.builder()
				.id(gameId)
				.league(league)
				.actualGameStartTime(actualStartTime)
				.scheduledGameStartTime(actualStartTime)
				.gameNumber(1)
				.patch("15.1")
				.gameLengthSeconds(1800)
				.ckpm(0.5)
				.build();

		game.addParticipant(createParticipant(game, blueTeamName, "Blue"));
		game.addParticipant(createParticipant(game, redTeamName, "Red"));
		return game;
	}

	private GameParticipant createParticipant(Game game, String teamName, String side) {
		Team team = Team.builder().name(teamName).code(teamName.substring(0, Math.min(3, teamName.length()))).build();
		Player player = Player.builder().name(teamName + " Player").build();
		Champion champion = Champion.builder()
				.championNameEn("Ahri")
				.championNameKr("아리")
				.imageUrl("https://example.com/ahri.png")
				.build();
		return GameParticipant.builder()
				.game(game)
				.team(team)
				.player(player)
				.champion(champion)
				.side(side)
				.position("MID")
				.isWin("Blue".equals(side))
				.build();
	}
}
