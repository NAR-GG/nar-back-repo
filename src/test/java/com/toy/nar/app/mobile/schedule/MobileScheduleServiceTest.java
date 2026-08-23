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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, null, 2, null);

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

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, null, null, null);

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

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, cursor, null, null);

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
	void getMatchPageWithoutAroundLeavesPastSideEmpty() {
		when(leagueMatchRepository.findMobileMatchPage("LCK", null, null, null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of(match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed")));

		MobileMatchPageResponse response = service.getMatchPage("LCK", null, null, null, null, null, null);

		assertThat(response.prevCursor()).isNull();
		assertThat(response.hasPrev()).isFalse();
	}

	@Test
	void getMatchPageAroundMergesPastAndFutureHalvesIntoAscendingOrder() {
		// around=2026-08-14(KST 00:00) → UTC 2026-08-13T15:00 이 앵커.
		LocalDateTime anchorUtc = LocalDateTime.of(2026, 8, 13, 15, 0);
		// 과거쪽은 내림차순으로 받는다(오버플로 1건 포함 → hasPrev).
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK", null, null, anchorUtc, "0", PageRequest.of(0, 3)))
				.thenReturn(List.of(
						match("match-13", "LCK", LocalDateTime.of(2026, 8, 12, 9, 0), "T1", "GEN", "completed"),
						match("match-12", "LCK", LocalDateTime.of(2026, 8, 11, 9, 0), "DK", "HLE", "completed"),
						match("match-11", "LCK", LocalDateTime.of(2026, 8, 10, 9, 0), "KT", "NS", "completed")));
		// 미래쪽은 앵커 이후 오름차순(오버플로 1건 포함 → hasNext).
		when(leagueMatchRepository.findMobileMatchPageAsc(
				"LCK", null, null, anchorUtc, null, null, PageRequest.of(0, 3)))
				.thenReturn(List.of(
						match("match-14", "LCK", LocalDateTime.of(2026, 8, 14, 9, 0), "T1", "DK", "unstarted"),
						match("match-15", "LCK", LocalDateTime.of(2026, 8, 15, 9, 0), "GEN", "HLE", "unstarted"),
						match("match-16", "LCK", LocalDateTime.of(2026, 8, 16, 9, 0), "KT", "T1", "unstarted")));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, null, 4, null, LocalDate.of(2026, 8, 14), null);

		// size=4 → 과거 2 + 미래 2, 그리고 과거→미래 한 줄로 이어진다.
		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-12", "match-13", "match-14", "match-15");
		assertThat(response.hasPrev()).isTrue();
		assertThat(response.hasNext()).isTrue();
		assertThat(decodeCursor(response.prevCursor())).isEqualTo("2026-08-11T09:00:00|match-12");
		assertThat(decodeCursor(response.nextCursor())).isEqualTo("2026-08-15T09:00:00|match-15");
	}

	@Test
	void getMatchPageAroundGivesOddSlotToFutureSide() {
		LocalDateTime anchorUtc = LocalDateTime.of(2026, 8, 13, 15, 0);
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK", null, null, anchorUtc, "0", PageRequest.of(0, 3)))
				.thenReturn(List.of());
		when(leagueMatchRepository.findMobileMatchPageAsc(
				"LCK", null, null, anchorUtc, null, null, PageRequest.of(0, 4)))
				.thenReturn(List.of());

		service.getMatchPage("LCK", null, null, null, null, 5, null, LocalDate.of(2026, 8, 14), null);

		// size=5 → 미래 3(+1) / 과거 2(+1). 남는 한 자리는 앞으로 볼 경기에 준다.
		verify(leagueMatchRepository).findMobileMatchPageAsc(
				"LCK", null, null, anchorUtc, null, null, PageRequest.of(0, 4));
		verify(leagueMatchRepository).findMobileMatchPage(
				"LCK", null, null, anchorUtc, "0", PageRequest.of(0, 3));
	}

	@Test
	void getMatchPageAroundReportsNoNeighborsWhenBothSidesFit() {
		LocalDateTime anchorUtc = LocalDateTime.of(2026, 8, 13, 15, 0);
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK", null, null, anchorUtc, "0", PageRequest.of(0, 2)))
				.thenReturn(List.of(match("match-13", "LCK", LocalDateTime.of(2026, 8, 12, 9, 0), "T1", "GEN", "completed")));
		when(leagueMatchRepository.findMobileMatchPageAsc(
				"LCK", null, null, anchorUtc, null, null, PageRequest.of(0, 2)))
				.thenReturn(List.of(match("match-14", "LCK", LocalDateTime.of(2026, 8, 14, 9, 0), "T1", "DK", "unstarted")));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, null, 2, null, LocalDate.of(2026, 8, 14), null);

		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-13", "match-14");
		assertThat(response.hasPrev()).isFalse();
		assertThat(response.prevCursor()).isNull();
		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void getMatchPageAroundUsesTeamFilterOnBothSides() {
		LocalDateTime anchorUtc = LocalDateTime.of(2026, 8, 13, 15, 0);
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team(1L, "T1", "T1", "https://example.com/t1.png")));
		when(leagueMatchRepository.findMobileTeamMatchPage(
				"LCK", "T1", "T1", null, null, anchorUtc, "0", PageRequest.of(0, 11)))
				.thenReturn(List.of());
		when(leagueMatchRepository.findMobileTeamMatchPageAsc(
				"LCK", "T1", "T1", null, null, anchorUtc, null, null, PageRequest.of(0, 11)))
				.thenReturn(List.of());

		service.getMatchPage("LCK", 1L, null, null, null, 20, null, LocalDate.of(2026, 8, 14), null);

		verify(leagueMatchRepository).findMobileTeamMatchPage(
				"LCK", "T1", "T1", null, null, anchorUtc, "0", PageRequest.of(0, 11));
		verify(leagueMatchRepository).findMobileTeamMatchPageAsc(
				"LCK", "T1", "T1", null, null, anchorUtc, null, null, PageRequest.of(0, 11));
	}

	@Test
	void getMatchPageBeforeReturnsPastRowsInAscendingOrder() {
		String before = encodeCursor("2026-08-14T09:00:00|match-14");
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK", null, null, LocalDateTime.of(2026, 8, 14, 9, 0), "match-14", PageRequest.of(0, 3)))
				.thenReturn(List.of(
						match("match-13", "LCK", LocalDateTime.of(2026, 8, 12, 9, 0), "T1", "GEN", "completed"),
						match("match-12", "LCK", LocalDateTime.of(2026, 8, 11, 9, 0), "DK", "HLE", "completed"),
						match("match-11", "LCK", LocalDateTime.of(2026, 8, 10, 9, 0), "KT", "NS", "completed")));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, null, 2, null, null, before);

		// 내림차순으로 받아 뒤집어 내린다 — 호출자가 기존 목록 앞에 그대로 붙일 수 있어야 한다.
		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-12", "match-13");
		assertThat(response.hasPrev()).isTrue();
		assertThat(decodeCursor(response.prevCursor())).isEqualTo("2026-08-11T09:00:00|match-12");
	}

	@Test
	void getMatchPageBeforeReportsNoMorePastWhenPageFits() {
		String before = encodeCursor("2026-08-14T09:00:00|match-14");
		when(leagueMatchRepository.findMobileMatchPage(
				"LCK", null, null, LocalDateTime.of(2026, 8, 14, 9, 0), "match-14", PageRequest.of(0, 3)))
				.thenReturn(List.of(match("match-13", "LCK", LocalDateTime.of(2026, 8, 12, 9, 0), "T1", "GEN", "completed")));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, null, 2, null, null, before);

		assertThat(response.matches()).hasSize(1);
		assertThat(response.hasPrev()).isFalse();
		assertThat(response.prevCursor()).isNull();
	}

	@Test
	void getMatchPageRejectsMixedEntryParameters() {
		String cursor = encodeCursor("2026-08-14T09:00:00|match-14");

		assertThatThrownBy(() -> service.getMatchPage(
				"LCK", null, null, null, cursor, 20, null, LocalDate.of(2026, 8, 14), null))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
		assertThatThrownBy(() -> service.getMatchPage(
				"LCK", null, null, null, cursor, 20, null, null, cursor))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
		assertThatThrownBy(() -> service.getMatchPage(
				"LCK", null, null, null, null, 20, null, LocalDate.of(2026, 8, 14), cursor))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
		verifyNoInteractions(leagueMatchRepository);
	}

	private String encodeCursor(String raw) {
		return java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private String decodeCursor(String cursor) {
		return new String(
				java.util.Base64.getUrlDecoder().decode(cursor),
				java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	void getMatchPageUsesTeamFilterWhenTeamIdExists() {
		Team t1 = team(1L, "T1", "T1", "https://example.com/t1.png");
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		when(leagueMatchRepository.findMobileTeamMatchPage("LCK", "T1", "T1", null, null, null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of());

		MobileMatchPageResponse response = service.getMatchPage("LCK", 1L, null, null, null, null, null);

		assertThat(response.teamId()).isEqualTo(1L);
		verify(leagueMatchRepository).findMobileTeamMatchPage("LCK", "T1", "T1", null, null, null, null, PageRequest.of(0, 21));
	}

	@Test
	void getMatchPagePassesSeasonFilterToRepository() {
		when(leagueMatchRepository.findMobileMatchPage("LCK", 2026, "Spring", null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of());

		service.getMatchPage("LCK", null, 2026, "Spring", null, null, null);

		verify(leagueMatchRepository).findMobileMatchPage("LCK", 2026, "Spring", null, null, PageRequest.of(0, 21));
	}

	/** from 이 있으면 오름차순 쿼리로 가고, KST 00:00 이 UTC 로 변환돼 넘어간다. */
	@Test
	void getMatchPageWithFromUsesAscendingQueryAndKstMidnightInUtc() {
		LeagueMatch first = match("match-1", "LCK", LocalDateTime.of(2026, 8, 9, 9, 0), "T1", "GEN", "unstarted");
		LeagueMatch second = match("match-2", "LCK", LocalDateTime.of(2026, 8, 10, 9, 0), "DK", "HLE", "unstarted");
		LeagueMatch overflow = match("match-3", "LCK", LocalDateTime.of(2026, 8, 11, 9, 0), "KT", "NS", "unstarted");
		LocalDateTime fromUtc = LocalDateTime.of(2026, 8, 8, 15, 0); // KST 2026-08-09 00:00
		when(leagueMatchRepository.findMobileMatchPageAsc("LCK", null, null, fromUtc, null, null, PageRequest.of(0, 3)))
				.thenReturn(List.of(first, second, overflow));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, null, 2, LocalDate.of(2026, 8, 9));

		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-1", "match-2");
		assertThat(response.hasNext()).isTrue();
		assertThat(new String(
				java.util.Base64.getUrlDecoder().decode(response.nextCursor()),
				java.nio.charset.StandardCharsets.UTF_8))
				.isEqualTo("2026-08-10T09:00:00|match-2");
		verify(leagueMatchRepository, never()).findMobileMatchPage(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	/** 오름차순 다음 페이지: 커서는 방향과 무관한 (matchDate, id) 그대로 되돌아온다. */
	@Test
	void getMatchPageWithFromAndCursorContinuesAscending() {
		String cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				"2026-08-10T09:00:00|match-2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		LocalDateTime fromUtc = LocalDateTime.of(2026, 8, 8, 15, 0);
		when(leagueMatchRepository.findMobileMatchPageAsc(
				"LCK", null, null, fromUtc, LocalDateTime.of(2026, 8, 10, 9, 0), "match-2", PageRequest.of(0, 21)))
				.thenReturn(List.of(match("match-3", "LCK", LocalDateTime.of(2026, 8, 11, 9, 0), "KT", "NS", "unstarted")));

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", null, null, null, cursor, null, LocalDate.of(2026, 8, 9));

		assertThat(response.matches()).extracting(MobileScheduleListResponse.MobileMatchSummary::matchId)
				.containsExactly("match-3");
		assertThat(response.hasNext()).isFalse();
		verify(leagueMatchRepository).findMobileMatchPageAsc(
				"LCK", null, null, fromUtc, LocalDateTime.of(2026, 8, 10, 9, 0), "match-2", PageRequest.of(0, 21));
	}

	@Test
	void getMatchPageWithFromAndTeamIdUsesAscendingTeamQuery() {
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team(1L, "T1", "T1", "https://example.com/t1.png")));
		LocalDateTime fromUtc = LocalDateTime.of(2026, 8, 8, 15, 0);
		when(leagueMatchRepository.findMobileTeamMatchPageAsc(
				"LCK", "T1", "T1", null, null, fromUtc, null, null, PageRequest.of(0, 21)))
				.thenReturn(List.of());

		MobileMatchPageResponse response = service.getMatchPage(
				"LCK", 1L, null, null, null, null, LocalDate.of(2026, 8, 9));

		assertThat(response.teamId()).isEqualTo(1L);
		verify(leagueMatchRepository).findMobileTeamMatchPageAsc(
				"LCK", "T1", "T1", null, null, fromUtc, null, null, PageRequest.of(0, 21));
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
		assertThatThrownBy(() -> service.getMatchPage("LCK", null, null, null, "not-a-cursor", null, null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	void getMatchGamesReturnsMappedGames() {
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "LCK", LocalDateTime.of(2026, 4, 1, 9, 0), "T1", "GEN", "completed")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", 100L, null, null)));

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
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
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
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
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
	void getMatchGamesMarksSetLiveFromFreshSnapshotsWhenStoreIsEmpty() {
		// 웹 파드 상황. #442 로 폴링이 스케줄러 파드로 가서 여기 store 는 영구히 비어 있다.
		// 인메모리만 보면 진행 중인 세트가 recordedSet 에 걸려 ENDED 로 내려간다 —
		// 실측 2026-08-23 16:14 TT vs LGD 에서 앱에 그렇게 나갔다. DB 신선도로 LIVE 를 살린다.
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "LPL", LocalDateTime.of(2026, 8, 23, 16, 0), "TT", "LGD", "inProgress")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(liveStateStore.isFinished("game-1")).thenReturn(false);
		// 프레임이 쌓여 있으니 recordedSet 에는 들어간다 — 보강이 없으면 이것 때문에 ENDED 다.
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1"))
				.thenReturn(List.of("game-1"));
		when(minuteSnapshotRepository.findFreshGameIdsByMatchId(eq("match-1"), any()))
				.thenReturn(List.of("game-1"));

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::status)
				.isEqualTo("LIVE");
	}

	@Test
	void getMatchGamesDoesNotResurrectFinishedSetFromFreshSnapshots() {
		// 스케줄러 파드 상황. 종료 확정된 세트는 stale 제거 전까지 신선한 프레임을 갖고 있다.
		// DB 보강에도 isFinished 필터를 걸어야 종료된 세트가 LIVE 로 부활하지 않는다.
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "LPL", LocalDateTime.of(2026, 8, 23, 16, 0), "TT", "LGD", "inProgress")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(liveStateStore.isFinished("game-1")).thenReturn(true);
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1"))
				.thenReturn(List.of("game-1"));
		when(minuteSnapshotRepository.findFreshGameIdsByMatchId(eq("match-1"), any()))
				.thenReturn(List.of("game-1"));

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::status)
				.isEqualTo("ENDED");
	}

	@Test
	void getMatchGamesAsksSnapshotsForFreshnessInUtc() {
		// frameTimestampUtc 는 UTC 다. 호출부가 시스템 존으로 물으면 KST(+9)에서는
		// 9시간 미래 기준이 되어 신선한 세트가 하나도 안 잡힌다.
		when(leagueMatchRepository.findById("match-1"))
				.thenReturn(Optional.of(match("match-1", "LPL", LocalDateTime.of(2026, 8, 23, 16, 0), "TT", "LGD", "inProgress")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());

		service.getMatchGames("match-1");

		ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(minuteSnapshotRepository).findFreshGameIdsByMatchId(eq("match-1"), since.capture());
		LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
		assertThat(since.getValue())
				.isAfter(nowUtc.minusMinutes(4))
				.isBefore(nowUtc.minusMinutes(2));
	}

	@Test
	void getMatchGamesReturnsWinnerTeamCodeFromRecordedGame() {
		// 적재된 경기 기록의 승리 팀 외부 id 로 매치의 blue/red 팀 코드를 되짚는다.
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(
				scoredMatch("match-1", "LCK", "T1", "ext-blue", 2, "GEN", "ext-red", 1, "completed")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", 100L, "ext-red", "GENG")));
		markEnded();

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.isEqualTo("GEN");
	}

	@Test
	void getMatchGamesFallsBackToSweepWinnerWhenRecordMissing() {
		// CSV 미적재 리그(LPL 등)에서도 완봉이면 모든 세트 승자가 확정된다.
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(
				scoredMatch("match-1", "LPL", "BLG", "ext-blue", 2, "TES", "ext-red", 0, "completed")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
		markEnded();

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.isEqualTo("BLG");
	}

	@Test
	void getMatchGamesLeavesWinnerNullWhenUndecidable() {
		// 기록도 없고 완봉도 아니면(2-1) 세트별 승자를 알 수 없다.
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(
				scoredMatch("match-1", "KESPA", "DNS", "ext-blue", 2, "BRO", "ext-red", 1, "completed")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", null, null, null)));
		markEnded();

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.extracting(MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.isNull();
	}

	@Test
	void getMatchGamesHidesWinnerWhileSetIsLive() {
		// 진행 중인 세트는 승자를 알 수 있어도 노출하지 않는다(계약: ENDED 아니면 null).
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(
				scoredMatch("match-1", "LCK", "T1", "ext-blue", 1, "GEN", "ext-red", 0, "inProgress")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(new WinnerRow("match-1", 1, "game-1", 100L, "ext-blue", "T1")));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>(java.util.Map.of(
				"game-1", new com.toy.nar.app.lolesports.live.ActiveLiveGame(
						"game-1", "match-1", "LCK", "T1", "GEN", LocalDateTime.of(2026, 7, 17, 13, 50), 0))));
		when(liveStateStore.isFinished("game-1")).thenReturn(false);

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games()).singleElement()
				.satisfies(game -> {
					assertThat(game.status()).isEqualTo("LIVE");
					assertThat(game.winnerTeamCode()).isNull();
				});
	}

	/** 라이브 스토어는 비었고 스냅샷은 있는 상태 = ENDED. */
	private void markEnded() {
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1")).thenReturn(List.of("game-1"));
	}

	private LeagueMatch scoredMatch(
			String id,
			String league,
			String blueCode,
			String blueExternalTeamId,
			int blueScore,
			String redCode,
			String redExternalTeamId,
			int redScore,
			String state) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchTitle(blueCode + " vs " + redCode)
				.matchDate(LocalDateTime.of(2026, 7, 20, 8, 0))
				.state(state)
				.blueTeamName(blueCode)
				.blueTeamCode(blueCode)
				.blueExternalTeamId(blueExternalTeamId)
				.blueScore(blueScore)
				.redTeamName(redCode)
				.redTeamCode(redCode)
				.redExternalTeamId(redExternalTeamId)
				.redScore(redScore)
				.build();
	}

	@Test
	void getMatchGamesMarksCompletedMatchSetsAsEndedWithoutSnapshots() {
		// 라이브 수집 이전 경기(스냅샷 없음)도 완료된 매치라면 치러진 세트다 — ENDED 로 내려야 승자가 노출된다.
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(
				scoredMatch("match-1", "LEC", "VIT", "ext-blue", 2, "MKOI", "ext-red", 1, "completed")));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(
						new WinnerRow("match-1", 1, "game-1", 10L, "ext-red", "MKOI"),
						new WinnerRow("match-1", 2, "game-2", 11L, "ext-blue", "VIT")));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1")).thenReturn(List.of());

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games())
				.extracting(
						MobileScheduleListResponse.MobileGameSummary::status,
						MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("ENDED", "MKOI"),
						org.assertj.core.groups.Tuple.tuple("ENDED", "VIT"));
	}

	@Test
	void getMatchGamesReadsWinnerFromScoreTransitionLog() {
		// 경기 기록(CSV)이 없어도 스코어 전이로 적어둔 set_winners 로 세트 승자를 낸다 — 라이브 직후 2-1 케이스.
		LeagueMatch match = scoredMatch("match-1", "KESPA", "DNS", "ext-blue", 2, "BRO", "ext-red", 1, "completed");
		match.applySetWinners("R,B,B");
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(match));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(
						new WinnerRow("match-1", 1, "game-1", null, null, null),
						new WinnerRow("match-1", 2, "game-2", null, null, null),
						new WinnerRow("match-1", 3, "game-3", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1")).thenReturn(List.of());

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games())
				.extracting(MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.containsExactly("BRO", "DNS", "DNS");
	}

	@Test
	void getMatchGamesSkipsUnknownMarkInScoreTransitionLog() {
		// '?' 세트(순서 미상)는 지어내지 않고 null — 나머지 세트는 정상 귀속.
		LeagueMatch match = scoredMatch("match-1", "KESPA", "DNS", "ext-blue", 2, "BRO", "ext-red", 1, "completed");
		match.applySetWinners("?,?,B");
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(match));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(
						new WinnerRow("match-1", 1, "game-1", null, null, null),
						new WinnerRow("match-1", 2, "game-2", null, null, null),
						new WinnerRow("match-1", 3, "game-3", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>());
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1")).thenReturn(List.of());

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games())
				.extracting(MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.containsExactly(null, null, "DNS");
	}

	@Test
	void getMatchGamesShowsTransitionWinnerDuringLiveMatch() {
		// 진행 중 매치의 끝난 세트 — 스코어 전이 기록이 있으면 라이브 중에도 승자를 낸다.
		LeagueMatch match = scoredMatch("match-1", "LCK", "T1", "ext-blue", 1, "GEN", "ext-red", 0, "inProgress");
		match.applySetWinners("B");
		when(leagueMatchRepository.findById("match-1")).thenReturn(Optional.of(match));
		when(leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId("match-1", "LOLESPORTS"))
				.thenReturn(List.of(
						new WinnerRow("match-1", 1, "game-1", null, null, null),
						new WinnerRow("match-1", 2, "game-2", null, null, null)));
		when(liveStateStore.getActiveGames()).thenReturn(new java.util.HashMap<>(java.util.Map.of(
				"game-2", new com.toy.nar.app.lolesports.live.ActiveLiveGame(
						"game-2", "match-1", "LCK", "T1", "GEN", LocalDateTime.of(2026, 7, 31, 19, 0), 0))));
		when(liveStateStore.isFinished("game-1")).thenReturn(false);
		when(liveStateStore.isFinished("game-2")).thenReturn(false);
		when(minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart("match-1")).thenReturn(List.of("game-1"));

		MobileMatchGamesResponse response = service.getMatchGames("match-1");

		assertThat(response.games())
				.extracting(
						MobileScheduleListResponse.MobileGameSummary::status,
						MobileScheduleListResponse.MobileGameSummary::winnerTeamCode)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("ENDED", "T1"),
						org.assertj.core.groups.Tuple.tuple("LIVE", null));
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

	private record WinnerRow(
			String rowMatchId,
			Integer rowGameOrder,
			String rowExternalGameId,
			Long rowInternalGameId,
			String rowWinnerExternalTeamId,
			String rowWinnerTeamCode) implements LeagueMatchGameRepository.MappedGameWinnerRow {

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

		@Override
		public String getWinnerExternalTeamId() {
			return rowWinnerExternalTeamId;
		}

		@Override
		public String getWinnerTeamCode() {
			return rowWinnerTeamCode;
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
