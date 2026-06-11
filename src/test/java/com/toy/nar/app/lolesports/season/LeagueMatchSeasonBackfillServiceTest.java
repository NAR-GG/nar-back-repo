package com.toy.nar.app.lolesports.season;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeagueMatchSeasonBackfillServiceTest {

	private LeagueMatchRepository leagueMatchRepository;
	private LeagueSeasonResolver seasonResolver;
	private LeagueMatchSeasonBackfillService service;

	@BeforeEach
	void setUp() {
		leagueMatchRepository = mock(LeagueMatchRepository.class);
		seasonResolver = mock(LeagueSeasonResolver.class);
		service = new LeagueMatchSeasonBackfillService(leagueMatchRepository, seasonResolver);
	}

	@Test
	void dryRunCountsTargetsWithoutUpdating() {
		when(leagueMatchRepository.findDistinctLeagueNames()).thenReturn(List.of("LCK"));
		when(seasonResolver.windowsFor("LCK")).thenReturn(List.of(window(
				"lck_spring_2026", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 20), 2026, "Spring")));
		when(leagueMatchRepository.countSeasonFillTargets(
				"LCK",
				LocalDateTime.of(2026, 1, 10, 0, 0),
				LocalDateTime.of(2026, 4, 21, 0, 0)))
				.thenReturn(42L);
		when(leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull("LCK")).thenReturn(42L);

		LeagueMatchSeasonBackfillService.BackfillResult result = service.backfill(true, false);

		assertThat(result.dryRun()).isTrue();
		assertThat(result.totalMatched()).isEqualTo(42);
		assertThat(result.leagues()).singleElement().satisfies(league -> {
			assertThat(league.league()).isEqualTo("LCK");
			assertThat(league.windows()).singleElement()
					.satisfies(window -> assertThat(window.matchedCount()).isEqualTo(42));
		});
		verify(leagueMatchRepository, never()).fillSeasonForRange(anyString(), any(), any(), any(), any());
		verify(leagueMatchRepository, never()).clearSeasonForLeague(anyString());
	}

	@Test
	void appliesShorterWindowsFirstSoSpecificTournamentWins() {
		when(leagueMatchRepository.findDistinctLeagueNames()).thenReturn(List.of("LCK"));
		// 전체 시즌(긴 기간)과 그 안에 포함된 컵(짧은 기간)이 겹치는 상황
		when(seasonResolver.windowsFor("LCK")).thenReturn(List.of(
				window("lck_2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31), 2026, "Season"),
				window("lck_cup_2026", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), 2026, "Cup")));
		when(leagueMatchRepository.fillSeasonForRange(anyString(), any(), any(), any(), any())).thenReturn(10);
		when(leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull("LCK")).thenReturn(0L);

		service.backfill(false, false);

		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(leagueMatchRepository);
		inOrder.verify(leagueMatchRepository).fillSeasonForRange(
				"LCK",
				LocalDateTime.of(2026, 1, 10, 0, 0),
				LocalDateTime.of(2026, 2, 11, 0, 0),
				2026,
				"Cup");
		inOrder.verify(leagueMatchRepository).fillSeasonForRange(
				"LCK",
				LocalDateTime.of(2026, 1, 1, 0, 0),
				LocalDateTime.of(2026, 9, 1, 0, 0),
				2026,
				"Season");
	}

	@Test
	void forceClearsSeasonsBeforeRefilling() {
		when(leagueMatchRepository.findDistinctLeagueNames()).thenReturn(List.of("LCK"));
		when(seasonResolver.windowsFor("LCK")).thenReturn(List.of(window(
				"lck_spring_2026", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 20), 2026, "Spring")));
		when(leagueMatchRepository.fillSeasonForRange(anyString(), any(), any(), any(), any())).thenReturn(5);
		when(leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull("LCK")).thenReturn(0L);

		service.backfill(false, true);

		verify(leagueMatchRepository).clearSeasonForLeague("LCK");
	}

	@Test
	void leagueWithoutTournamentInfoIsSkippedSafely() {
		when(leagueMatchRepository.findDistinctLeagueNames()).thenReturn(List.of("UNKNOWN"));
		when(seasonResolver.windowsFor("UNKNOWN")).thenReturn(List.of());
		when(leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull("UNKNOWN")).thenReturn(7L);

		LeagueMatchSeasonBackfillService.BackfillResult result = service.backfill(false, false);

		assertThat(result.leagues()).singleElement().satisfies(league -> {
			assertThat(league.windows()).isEmpty();
			assertThat(league.remainingWithoutSeason()).isEqualTo(7);
		});
		verify(leagueMatchRepository, never()).fillSeasonForRange(anyString(), any(), any(), any(), any());
	}

	private LeagueSeasonResolver.SeasonWindow window(
			String slug, LocalDate start, LocalDate end, int year, String split) {
		return new LeagueSeasonResolver.SeasonWindow(
				slug, start, end, new LeagueSeasonResolver.LeagueSeason(year, split));
	}
}
