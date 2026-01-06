package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.ChampionAnalysisResponse;
import com.toy.nar.app.analysis.dto.ChampionStatsDto;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
				.champions(List.of())
				.build();
		}

		// 2. 연도 정보 조회 (해당 패치의 가장 최신 경기 기준)
		List<Integer> years = gameRepository.findYearsByLeagueAndPatch(targetLeague, latestPatch, PageRequest.of(0, 1));
		String year = years.isEmpty() ? "" : String.valueOf(years.get(0));

		// 3. 해당 패치 & 리그에서 가장 많이 플레이된 챔피언 TOP 5 조회
		List<ChampionStatsDto> champions = gameRepository.findChampionStatsByPatchAndLeague(latestPatch, targetLeague, PageRequest.of(0, 5));

		return ChampionAnalysisResponse.builder()
			.patchVersion(latestPatch)
			.seasonInfo(year) // 예: "2024"
			.champions(champions)
			.build();
	}
}
