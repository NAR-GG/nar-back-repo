package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.PlayerAnalysisResponse;
import com.toy.nar.app.analysis.dto.PlayerStatsDto;
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
public class PlayerAnalysisService {

	private final GameRepository gameRepository;

	@Transactional(readOnly = true)
	public PlayerAnalysisResponse getTop5PlayersByLatestPatch() {
		String targetLeague = "LCK";

		// 1. LCK의 최신 패치 버전 조회
		String latestPatch = gameRepository.findLatestPatchByLeague(targetLeague);
		if (latestPatch == null) {
			return PlayerAnalysisResponse.builder()
				.patchVersion("N/A")
				.seasonInfo("N/A")
				.kdaTop5(List.of())
				.gpmTop5(List.of())
				.dpmTop5(List.of())
				.build();
		}

		// 2. 연도 정보 조회
		List<Integer> years = gameRepository.findYearsByLeagueAndPatch(targetLeague, latestPatch, PageRequest.of(0, 1));
		String year = years.isEmpty() ? "" : String.valueOf(years.get(0));

		// 3. 각 지표별 TOP 5 조회
		PageRequest pageable = PageRequest.of(0, 5);
		List<PlayerStatsDto> kdaTop5 = gameRepository.findTopKdaPlayersByPatchAndLeague(latestPatch, targetLeague, pageable);
		List<PlayerStatsDto> gpmTop5 = gameRepository.findTopGpmPlayersByPatchAndLeague(latestPatch, targetLeague, pageable);
		List<PlayerStatsDto> dpmTop5 = gameRepository.findTopDpmPlayersByPatchAndLeague(latestPatch, targetLeague, pageable);

		return PlayerAnalysisResponse.builder()
			.patchVersion(latestPatch)
			.seasonInfo(year)
			.kdaTop5(kdaTop5)
			.gpmTop5(gpmTop5)
			.dpmTop5(dpmTop5)
			.build();
	}
}
