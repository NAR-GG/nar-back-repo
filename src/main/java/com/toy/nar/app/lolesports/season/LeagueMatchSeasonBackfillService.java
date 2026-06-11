package com.toy.nar.app.lolesports.season;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * league_match의 시즌 컬럼(season_year/season_split)을 토너먼트 기간 기준으로 채우는 백필.
 *
 * 안전 설계:
 * - season_year IS NULL 인 행만 갱신한다 (기존 값 보존, 멱등)
 * - 겹치는 토너먼트 기간은 짧은(더 구체적인) 기간부터 적용한다
 * - force=true 면 해당 리그의 시즌 값을 먼저 비우고 다시 채운다 (재계산)
 * - dryRun=true 면 갱신 없이 대상 건수만 집계한다
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueMatchSeasonBackfillService {

	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueSeasonResolver seasonResolver;

	public record WindowResult(
			String tournamentSlug,
			Integer seasonYear,
			String seasonSplit,
			String startDate,
			String endDate,
			long matchedCount) {
	}

	public record LeagueResult(
			String league,
			List<WindowResult> windows,
			long remainingWithoutSeason) {
	}

	public record BackfillResult(
			boolean dryRun,
			boolean force,
			long totalMatched,
			List<LeagueResult> leagues) {
	}

	@Transactional
	public BackfillResult backfill(boolean dryRun, boolean force) {
		List<String> leagues = leagueMatchRepository.findDistinctLeagueNames();
		List<LeagueResult> leagueResults = new ArrayList<>();
		long totalMatched = 0;

		for (String league : leagues) {
			List<LeagueSeasonResolver.SeasonWindow> windows = seasonResolver.windowsFor(league).stream()
					.sorted(Comparator.comparingLong(LeagueSeasonResolver.SeasonWindow::durationDays))
					.toList();
			if (windows.isEmpty()) {
				log.warn("시즌 백필 - 토너먼트 정보를 찾지 못해 건너뜀: {}", league);
				leagueResults.add(new LeagueResult(
						league,
						List.of(),
						leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull(league)));
				continue;
			}

			if (force && !dryRun) {
				int cleared = leagueMatchRepository.clearSeasonForLeague(league);
				log.info("시즌 백필 - {} 시즌 값 초기화: {}건", league, cleared);
			}

			List<WindowResult> windowResults = new ArrayList<>();
			for (LeagueSeasonResolver.SeasonWindow window : windows) {
				LocalDateTime start = window.startDate().atStartOfDay();
				LocalDateTime end = window.endDate().plusDays(1).atStartOfDay();
				long matched = dryRun
						? leagueMatchRepository.countSeasonFillTargets(league, start, end)
						: leagueMatchRepository.fillSeasonForRange(
								league, start, end, window.season().year(), window.season().split());
				totalMatched += matched;
				windowResults.add(new WindowResult(
						window.tournamentSlug(),
						window.season().year(),
						window.season().split(),
						window.startDate().toString(),
						window.endDate().toString(),
						matched));
			}

			long remaining = leagueMatchRepository.countByLeagueNameAndSeasonYearIsNull(league);
			leagueResults.add(new LeagueResult(league, windowResults, remaining));
			log.info("시즌 백필 - {} 완료 (dryRun={}): 미해석 잔여 {}건", league, dryRun, remaining);
		}

		return new BackfillResult(dryRun, force, totalMatched, leagueResults);
	}
}
