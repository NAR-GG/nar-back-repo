package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.ChampionAnalysisResponse;
import com.toy.nar.app.analysis.dto.ChampionBanStatsDto;
import com.toy.nar.app.analysis.dto.ChampionStatsDto;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionAnalysisService {

	private final GameRepository gameRepository;

	@Transactional(readOnly = true)
	public ChampionAnalysisResponse getMostPlayedChampionsByLatestPatch() {
		String targetLeague = "LCK";

		// 1. LCK의 최신 패치 버전 조회
		String latestPatch = gameRepository.findLatestPatchByLeague(targetLeague);
		if (latestPatch == null) {
			return ChampionAnalysisResponse.builder()
					.patchVersion("N/A")
					.seasonInfo("N/A")
					.topPicks(List.of())
					.topBans(List.of())
					.build();
		}

		// 2. 연도 정보 조회
		List<Integer> years = gameRepository.findYearsByLeagueAndPatch(targetLeague, latestPatch, PageRequest.of(0, 1));
		String year = years.isEmpty() ? "" : String.valueOf(years.get(0));

		// 3. 픽률/승률 통계 TOP 5 조회
		List<ChampionStatsDto> pickStats = gameRepository.findChampionStatsByPatchAndLeague(latestPatch, targetLeague,
				PageRequest.of(0, 5));

		// 4. 밴률 통계 TOP 5 조회
		List<ChampionBanStatsDto> top5Bans = gameRepository
				.findChampionBanStatsByPatchAndLeague(latestPatch, targetLeague)
				.stream().limit(5).collect(Collectors.toList());

		// 5. 밴된 챔피언들의 픽/승률 통계 조회
		if (!top5Bans.isEmpty()) {
			List<String> bannedChampionNames = top5Bans.stream()
					.map(ChampionBanStatsDto::getChampionNameKr)
					.toList();

			Map<String, ChampionStatsDto> pickWinStatsMap = gameRepository
					.findChampionStatsByNamesAndPatch(latestPatch, targetLeague, bannedChampionNames)
					.stream()
					.collect(Collectors.toMap(ChampionStatsDto::getChampionNameKr, stats -> stats));

			// 6. 데이터 조합
			top5Bans.forEach(banStat -> banStat.setPickWinStats(pickWinStatsMap.get(banStat.getChampionNameKr())));
		}

		return ChampionAnalysisResponse.builder()
				.patchVersion(latestPatch)
				.seasonInfo(year)
				.topPicks(pickStats)
				.topBans(top5Bans)
				.build();
	}
}
