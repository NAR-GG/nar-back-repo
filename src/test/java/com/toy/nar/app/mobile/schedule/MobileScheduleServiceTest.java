package com.toy.nar.app.mobile.schedule;

import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchGamesResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchPageResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MobileScheduleServiceTest {

	private LeagueMatchRepository leagueMatchRepository;
	private LeagueMatchGameRepository leagueMatchGameRepository;
	private TeamRepository teamRepository;
	private LiveStateStore liveStateStore;
	private LiveGameMinuteSnapshotRepository minuteSnapshotRepository;
	private MobileScheduleService service;

	@BeforeEach
	void setUp() {
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		leagueMatchGameRepository = mock(LeagueMatchGameRepository.class);
		teamRepository = mock(TeamRepository.class);
		liveStateStore = mock(LiveStateStore.class);
		minuteSnapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
		service = new MobileScheduleService(leagueMatchRepository, leagueMatchGameRepository,
				teamRepository, liveStateStore, minuteSnapshotRepository);
	}

	@Test
	void getFiltersReturnsDefaultLeagueAndTeamsForSelectedLeague() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findAllByCodeIn(List.of(
				"T1", "HLE", "GEN", "DK", "KT",
				"DNS", "BFX", "NS", "BRO", "KRX"
		))).thenReturn(List.of(t1));

		MobileScheduleFilterResponse response = service.getFilters(null);

		assertThat(response.defaultLeague()).isEqualTo("LCK");
		assertThat(response.leagues()).extracting(MobileScheduleFilterResponse.LeagueOption::code)
				.contains("LCK", "LPL");
		assertThat(response.teams()).singleElement()
				.extracting(MobileScheduleFilterResponse.TeamOption::teamId,
						MobileScheduleFilterResponse.TeamOption::teamName,
						MobileScheduleFilterResponse.TeamOption::teamCode)
				.containsExactly(1L, "T1", "T1");
	}

	@Test
	void getCalendarGroupsLeagueMatchesByKstDate() {
		LocalDateTime startUtc = LocalDateTime.of(2026, 3, 31, 15, 0);
		LocalDateTime endUtc = LocalDateTime.of(2026, 4, 30, 15, 0);
		when(leagueMatchRepository.findMobileMatchesInRange(List.of("LCK"), startUtc, endUtc))
				.thenReturn(List.of(
						match("match-1", "LCK", LocalDateTime.of(2026, 3, 31, 15, 30), "T1", "GEN", "unstarted"),
						match("match-2", "LCK", LocalDateTime.of(2026, 4, 1, 10, 0), "DK", "HLE", "unstarted")));

		MobileScheduleCalendarResponse response = service.getCalendar(YearMonth.of(2026, 4), List.of("lck"), null);

		assertThat(response.month()).isEqualTo("2026-04");
		assertThat(response.league()).isEqualTo("LCK");
		assertThat(response.dates()).hasSize(1);
		assertThat(response.dates()).extracting(MobileScheduleCalendarResponse.DateSummary::date)
				.containsExactly("2026-04-01");
		assertThat(response.dates()).extracting(MobileScheduleCalendarResponse.DateSummary::matchCount)
				.containsExactly(2L);
		assertThat(response.dates().getFirst().matches()).hasSize(2);
		assertThat(response.dates().getFirst().matches()).extracting(MobileScheduleCalendarResponse.CalendarMatch::matchId)
				.containsExactly("match-1", "match-2");
		assertThat(response.dates().getFirst().matches().getFirst().blueTeamCode()).isEqualTo("T1");
		assertThat(response.dates().getFirst().matches().getFirst().redTeamCode()).isEqualTo("GEN");
		assertThat(response.dates().getFirst().matches().getFirst().displayText()).isEqualTo("T1 vs GEN");
	}

	@Test
	void getDailySchedulesUsesTeamFilterWhenTeamIdExists() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		when(leagueMatchRepository.findMobileTeamMatchesInRange(
				List.of("LCK"),
				List.of("t1"),
				List.of("t1"),
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of(match(
						"match-1",
						"LCK",
						LocalDateTime.of(2026, 4, 1, 9, 0),
						"T1",
						"Gen.G",
						"inProgress")));

		MobileScheduleListResponse response = service.getDailySchedules(
				LocalDate.of(2026, 4, 1),
				List.of("LCK"),
				List.of(1L));

		assertThat(response.date()).isEqualTo("2026-04-01");
		assertThat(response.teamId()).isEqualTo(1L);
		assertThat(response.matches()).singleElement()
				.satisfies(match -> {
					assertThat(match.matchId()).isEqualTo("match-1");
					assertThat(match.scheduledTime()).isEqualTo("18:00");
					assertThat(match.matchStatus()).isEqualTo("inProgress");
					assertThat(match.blueTeam().teamName()).isEqualTo("T1");
					assertThat(match.redTeam().teamName()).isEqualTo("Gen.g");
					// 대표 링크는 streamLinks 첫 번째(치지직 LCK 공식)와 동일해야 한다.
					assertThat(match.liveStreamUrl())
							.isEqualTo("https://chzzk.naver.com/9381e7d6816e6d915a44a13c0195b202");
					assertThat(match.streamLinks()).hasSize(2);
					assertThat(match.streamLinks().get(0).provider()).isEqualTo("chzzk");
					assertThat(match.streamLinks().get(1).provider()).isEqualTo("soop");
					assertThat(match.liveStreamUrl()).isEqualTo(match.streamLinks().get(0).url());
				});
	}

	@Test
	void getDailySchedulesQueriesOneKstDayAsUtcRange() {
		when(leagueMatchRepository.findMobileMatchesInRange(
				List.of("LCK"),
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of());

		service.getDailySchedules(LocalDate.of(2026, 4, 1), null, null);

		ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(leagueMatchRepository).findMobileMatchesInRange(
				org.mockito.ArgumentMatchers.eq(List.of("LCK")),
				startCaptor.capture(),
				endCaptor.capture());
		assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 3, 31, 15, 0));
		assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 1, 15, 0));
	}

	@Test
	void getDailySchedulesAttachesGamesPerMatch() {
		when(leagueMatchRepository.findMobileMatchesInRange(
				List.of("LCK"),
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of(match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed")));
		when(leagueMatchGameRepository.findMappedGameRowsByMatchIds(List.of("match-1"), "LOLESPORTS"))
				.thenReturn(List.of(
						new MappedRow("match-1", 1, "game-1", 100L),
						new MappedRow("match-1", 2, "game-2", null)));

		MobileScheduleListResponse response = service.getDailySchedules(LocalDate.of(2026, 4, 1), List.of("LCK"), null);

		assertThat(response.matches()).singleElement()
				.satisfies(match -> {
					assertThat(match.date()).isEqualTo("2026-04-01");
					assertThat(match.games()).extracting(
									MobileScheduleListResponse.MobileGameSummary::gameOrder,
									MobileScheduleListResponse.MobileGameSummary::gameId,
									MobileScheduleListResponse.MobileGameSummary::recordGameId)
							.containsExactly(
									org.assertj.core.groups.Tuple.tuple(1, "game-1", 100L),
									org.assertj.core.groups.Tuple.tuple(2, "game-2", null));
				});
	}

	@Test
	void getMatchPageReturnsNextCursorWhenMoreRowsExist() {
		LeagueMatch first = match("match-2", "LCK", LocalDateTime.of(2026, 4, 2, 9, 0), "T1", "GEN", "completed");
		LeagueMatch second = match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "DK", "HLE", "completed");
		LeagueMatch overflow = match("match-0", "LCK", LocalDateTime.of(2026, 3, 31, 9, 0), "KT", "NS", "completed");
		when(leagueMatchRepository.findMobileMatchPage("LCK", null, null, null, null, PageRequest.of(0, 3)))
				.thenReturn(List.of(first, second, overflow));

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, null, 2);

		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-2", "match-1");
		assertThat(response.hasNext()).isTrue();
		String decoded = new String(
				java.util.Base64.getUrlDecoder().decode(response.nextCursor()),
				java.nio.charset.StandardCharsets.UTF_8);
		assertThat(decoded).isEqualTo("2026-04-01T09:00:00|match-1");
	}

	@Test
	void getMatchPageReturnsNoCursorOnLastPage() {
		when(leagueMatchRepository.findMobileMatchPage("LCK", null, null, null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of(match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed")));

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, null, null);

		assertThat(response.matches()).hasSize(1);
		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void getMatchPagePassesDecodedCursorToRepository() {
		String cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				"2026-04-01T09:00:00|match-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK",
				null,
				null,
				LocalDateTime.of(2026, 4, 1, 9, 0),
				"match-1",
				PageRequest.of(0, 21)))
				.thenReturn(List.of());

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, cursor, null);

		assertThat(response.matches()).isEmpty();
		assertThat(response.hasNext()).isFalse();
		verify(leagueMatchRepository).findMobileMatchPage(
				"LCK",
				null,
				null,
				LocalDateTime.of(2026, 4, 1, 9, 0),
				"match-1",
				PageRequest.of(0, 21));
	}

	@Test
	void getMatchPageUsesTeamFilterWhenTeamIdExists() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		when(leagueMatchRepository.findMobileTeamMatchPage("LCK", "T1", "T1", null, null, null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of());

		MobileMatchPageResponse response = service.getMatchPage("LCK", 1L, null, null, null, null);

		assertThat(response.teamId()).isEqualTo(1L);
		verify(leagueMatchRepository).findMobileTeamMatchPage("LCK", "T1", "T1", null, null, null, null, PageRequest.of(0, 21));
	}

	@Test
	void getMatchPagePassesSeasonFilterToRepository() {
		when(leagueMatchRepository.findMobileMatchPage("LCK", 2026, "Spring", null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of());

		service.getMatchPage("LCK", null, 2026, "Spring", null, null);

		verify(leagueMatchRepository).findMobileMatchPage("LCK", 2026, "Spring", null, null, PageRequest.of(0, 21));
	}

	@Test
	void getFiltersIncludesSeasonOptions() {
		when(teamRepository.findAllByCodeIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
		LeagueMatchRepository.SeasonOptionRow row = mock(LeagueMatchRepository.SeasonOptionRow.class);
		when(row.getSeasonYear()).thenReturn(2026);
		when(row.getSeasonSplit()).thenReturn("Spring");
		when(leagueMatchRepository.findSeasonOptions("LCK")).thenReturn(List.of(row));

		MobileScheduleFilterResponse response = service.getFilters("LCK");

		assertThat(response.seasons()).singleElement()
				.satisfies(season -> {
					assertThat(season.year()).isEqualTo(2026);
					assertThat(season.split()).isEqualTo("Spring");
					assertThat(season.label()).isEqualTo("2026 Spring");
				});
	}

	@Test
	void getMatchPageWithInvalidCursorThrowsInvalidInput() {
		assertThatThrownBy(() -> service.getMatchPage("LCK", null, null, null, "not-a-cursor", null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	void getMatchGamesReturnsMappedGames() {
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed")));
		when(leagueMatchGameRepository.findMappedGameRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new MappedRow("match-1", 1, "game-1", 100L)));

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.matchId()).isEqualTo("match-1");
		assertThat(response.games()).singleElement()
				.satisfies(game -> {
					assertThat(game.gameOrder()).isEqualTo(1);
					assertThat(game.gameId()).isEqualTo("game-1");
					assertThat(game.recordGameId()).isEqualTo(100L);
				});
	}

	@Test
	void getMatchGamesMarksFrameFinishedGameAsEndedDespiteStoreResidue() {
		// 세트 종료 후 stale 3분 동안 게임이 store 에 남아 "LIVE" 잔상으로 표시되던 문제:
		// 프레임 finished 로 확정된 게임은 store 잔상과 무관하게 ENDED 로 내려야
		// SET_END 푸시(프레임 신호)와 상세 화면이 같은 시점에 종료로 보인다.
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "EWC", LocalDateTime.of(2026, 7, 17, 13, 30), "DK", "BLG", "inProgress")));
		when(leagueMatchGameRepository.findMappedGameRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new MappedRow("match-1", 1, "game-1", null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>(java.util.Map.of(
				"game-1", new com.toy.nar.app.lolesports.live.ActiveLiveGame(
						"game-1", "match-1", "EWC", "DK", "BLG", LocalDateTime.of(2026, 7, 17, 13, 50), 0))));
		when(liveStateStore.isFinished("game-1")).thenReturn(true);
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1"))
				.thenReturn(List.of("game-1"));

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::status)
				.isEqualTo("ENDED");
	}

	@Test
	void getMatchGamesKeepsLiveStatusWhileFrameNotFinished() {
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "EWC", LocalDateTime.of(2026, 7, 17, 13, 30), "DK", "BLG", "inProgress")));
		when(leagueMatchGameRepository.findMappedGameRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new MappedRow("match-1", 1, "game-1", null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>(java.util.Map.of(
				"game-1", new com.toy.nar.app.lolesports.live.ActiveLiveGame(
						"game-1", "match-1", "EWC", "DK", "BLG", LocalDateTime.of(2026, 7, 17, 13, 50), 0))));
		when(liveStateStore.isFinished("game-1")).thenReturn(false);

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::status)
				.isEqualTo("LIVE");
	}

	@Test
	void getMatchGamesWithUnknownMatchThrowsDataNotFound() {
		when(leagueMatchRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getMatchGames("missing"))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.DATA_NOT_FOUND);
	}

	@Test
	void invalidLeagueThrowsInvalidInput() {
		assertThatThrownBy(() -> service.getDailySchedules(LocalDate.of(2026, 4, 1), List.of("abc"), null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verifyNoInteractions(leagueMatchRepository);
	}

	@Test
	void allLeagueQueriesWithoutLeagueFilter() {
		when(leagueMatchRepository.findMobileMatchesInRange(
				null,
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0)))
				.thenReturn(List.of(
						match("m-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed"),
						match("m-2", "LPL", LocalDateTime.of(2026, 4, 1, 11, 0), "BLG", "JDG", "completed")));

		MobileScheduleListResponse response = service.getDailySchedules(LocalDate.of(2026, 4, 1), List.of("ALL"), null);

		assertThat(response.league()).isEqualTo("ALL");
		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("m-1", "m-2");
		verify(leagueMatchRepository).findMobileMatchesInRange(
				null,
				LocalDateTime.of(2026, 3, 31, 15, 0),
				LocalDateTime.of(2026, 4, 1, 15, 0));
	}

	@Test
	void getFiltersForAllLeagueReturnsCurrentSeasonTeamsUnion() {
		when(leagueMatchRepository.findSeasonOptions(null)).thenReturn(List.of());
		// ALL 은 리그 조건 없이(null) 현재 시즌 출전팀 코드 쌍을 조회한다. 빈 코드·중복은 걸러진다.
		when(leagueMatchRepository.findTeamCodePairsBySeason(null, 2026)).thenReturn(List.<Object[]>of(
				new Object[] {"T1", "GEN"},
				new Object[] {"GEN", ""},
				new Object[] {"BLG", null}));
		Team gen = team(2L, "Gen.G", "GEN", null);
		Team t1 = team(1L, "T1", "T1", null);
		Team blg = team(3L, "Bilibili Gaming", "BLG", null);
		when(teamRepository.findAllByCodeIn(org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(gen, t1, blg));

		MobileScheduleFilterResponse response = service.getFilters("ALL");

		assertThat(response.leagues()).first()
				.extracting(MobileScheduleFilterResponse.LeagueOption::code,
						MobileScheduleFilterResponse.LeagueOption::name)
				.containsExactly("ALL", "전체");
		// 이름순: Bilibili Gaming, Gen.G, T1
		assertThat(response.teams()).extracting(MobileScheduleFilterResponse.TeamOption::teamCode)
				.containsExactly("BLG", "GEN", "T1");
	}

	@Test
	void getFiltersForNonLckLeagueUsesCurrentSeasonMatchTeams() {
		when(leagueMatchRepository.findSeasonOptions("LPL")).thenReturn(List.of());
		when(leagueMatchRepository.findTeamCodePairsBySeason("LPL", 2026))
				.thenReturn(List.<Object[]>of(new Object[] {"BLG", "T1"}));
		when(teamRepository.findAllByCodeIn(org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(team(3L, "Bilibili Gaming", "BLG", null)));

		MobileScheduleFilterResponse response = service.getFilters("LPL");

		assertThat(response.teams()).extracting(MobileScheduleFilterResponse.TeamOption::teamCode)
				.containsExactly("BLG");
	}

	@Test
	void unknownTeamThrowsDataNotFound() {
		when(teamRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getCalendar(YearMonth.of(2026, 4), List.of("LCK"), List.of(999L)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.DATA_NOT_FOUND);
	}

	private record MappedRow(
			String rowMatchId,
			Integer rowGameOrder,
			String rowExternalGameId,
			Long rowInternalGameId) implements LeagueMatchGameRepository.MappedGameRow {

		@Override
		public String getMatchId() {
			return rowMatchId;
		}

		@Override
		public Integer getGameOrder() {
			return rowGameOrder;
		}

		@Override
		public String getExternalGameId() {
			return rowExternalGameId;
		}

		@Override
		public Long getInternalGameId() {
			return rowInternalGameId;
		}
	}

	private LeagueMatch match(
			String id,
			String league,
			LocalDateTime matchDate,
			String blueTeam,
			String redTeam,
			String state) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchTitle(blueTeam + " vs " + redTeam)
				.matchDate(matchDate)
				.state(state)
				.blueTeamName(blueTeam)
				.blueTeamCode(blueTeam)
				.blueTeamImageUrl("https://example.com/" + blueTeam + ".png")
				.blueScore(1)
				.redTeamName(redTeam)
				.redTeamCode(redTeam)
				.redTeamImageUrl("https://example.com/" + redTeam + ".png")
				.redScore(0)
				.build();
	}

	private Team team(Long id, String name, String code, String imageUrl) {
		Team team = Team.builder()
				.name(name)
				.code(code)
				.imageUrl(imageUrl)
				.build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}
}
