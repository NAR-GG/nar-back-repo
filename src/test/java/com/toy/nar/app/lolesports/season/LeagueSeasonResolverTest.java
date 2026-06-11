package com.toy.nar.app.lolesports.season;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueSeasonResolverTest {

	@Test
	void parsesStandardSplitSlugs() {
		assertSeason("LCK", "lck_spring_2026", 2026, "Spring");
		assertSeason("LCK", "lck_summer_2025", 2025, "Summer");
		assertSeason("LEC", "lec_winter_2026", 2026, "Winter");
		assertSeason("WORLDS", "worlds_2025", 2025, "Worlds");
		assertSeason("MSI", "msi_2026", 2026, "MSI");
	}

	@Test
	void parsesFirstStandAndCupSlugs() {
		assertSeason("FIRST_STAND", "first_stand_2026", 2026, "First Stand");
		assertSeason("LCK", "lck_cup_2026", 2026, "Cup");
	}

	@Test
	void fallsBackToStartDateYearWhenSlugHasNoYear() {
		LeagueSeasonResolver.LeagueSeason season = LeagueSeasonResolver.parseSeason(
				"LCK", "lck_spring", LocalDate.of(2026, 1, 15));
		assertThat(season.year()).isEqualTo(2026);
		assertThat(season.split()).isEqualTo("Spring");
	}

	@Test
	void unknownSlugUsesPrettifiedRemainderAsSplit() {
		LeagueSeasonResolver.LeagueSeason season = LeagueSeasonResolver.parseSeason(
				"LCK", "lck_2026_rounds_1_2", LocalDate.of(2026, 1, 15));
		assertThat(season.year()).isEqualTo(2026);
		assertThat(season.split()).isEqualTo("Rounds 1 2");
	}

	@Test
	void slugWithOnlyLeagueAndYearFallsBackToSeasonLabel() {
		LeagueSeasonResolver.LeagueSeason season = LeagueSeasonResolver.parseSeason(
				"LCK", "lck_2026", LocalDate.of(2026, 1, 15));
		assertThat(season.year()).isEqualTo(2026);
		assertThat(season.split()).isEqualTo("Season");
	}

	@Test
	void splitIsTruncatedToColumnLength() {
		LeagueSeasonResolver.LeagueSeason season = LeagueSeasonResolver.parseSeason(
				"LCK", "lck_2026_super_long_tournament_stage_name_overflow", LocalDate.of(2026, 1, 15));
		assertThat(season.split().length()).isLessThanOrEqualTo(20);
	}

	private void assertSeason(String league, String slug, int expectedYear, String expectedSplit) {
		LeagueSeasonResolver.LeagueSeason season = LeagueSeasonResolver.parseSeason(
				league, slug, LocalDate.of(expectedYear, 1, 1));
		assertThat(season).isNotNull();
		assertThat(season.year()).isEqualTo(expectedYear);
		assertThat(season.split()).isEqualTo(expectedSplit);
	}
}
