package com.toy.nar.api.v1;

import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.PageCombinationResponse;
import com.toy.nar.app.analysis.dto.PageMatchupResponse;
import com.toy.nar.app.analysis.dto.UpdateInfoDto;
import com.toy.nar.app.analysis.service.CombinationService;
import com.toy.nar.app.analysis.service.MatchupService;
import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Tag(name = "1. 챔피언 조합 분석", description = "챔피언 조합 승률, 1vs1 매치업 분석 데이터를 제공합니다.")
@RestController
@RequestMapping("/api/combinations")
@RequiredArgsConstructor
public class CombinationController {

	private final CombinationService combinationService;
	private final MatchupService matchupService;

	@Operation(summary = "챔피언 조합 조회", description = "선택한 챔피언들의 조합 승률, 픽률 데이터를 조회합니다.")
	@GetMapping("/")
	public ResponseEntity<PageCombinationResponse> getCombinationsV2(
		@Parameter(description = "포함할 챔피언 이름 리스트 (예: Ahri,Galio)", required = true)
		@RequestParam("champions") List<String> champions,

		@Parameter(description = "경기 연도 (미입력 시 전체)")
		@RequestParam(value = "year", required = false) Optional<Integer> year,

		@Parameter(description = "스플릿 (Cup, Rounds 1-2 등)")
		@RequestParam(value = "splits", required = false) Optional<List<String>> splits,

		@Parameter(description = "리그 이름 (LCK, LPL 등)")
		@RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,

		@Parameter(description = "팀 이름")
		@RequestParam(value = "teamNames", required = false) Optional<List<String>> teamNames,

		@Parameter(description = "게임 패치 버전")
		@RequestParam(value = "patch", required = false) Optional<String> patch,

		@Parameter(description = "페이지 번호 (0부터 시작)")
		@RequestParam(value = "page", defaultValue = "0") int page,

		@Parameter(description = "페이지 크기")
		@RequestParam(value = "size", defaultValue = "10") int size,

		@Parameter(description = "정렬 기준 (frequency)")
		@RequestParam(value = "sort", defaultValue = "frequency") String sort) {

		MultiCombinationFilterDto filter = buildFilter(year, splits, leagueNames, teamNames, patch);

		Pageable basicPageable = PageRequest.of(page, size);
		Pageable pageable = combinationService.applyDynamicSort(basicPageable, sort);

		PageCombinationResponse combinations = combinationService.findTopCombinationsV3(champions, filter, pageable);
		return ResponseEntity.ok(combinations);
	}

	@Operation(summary = "1vs1 라인전 매치업 조회", description = "두 챔피언 간의 1:1 상대 전적 및 지표를 조회합니다.")
	@GetMapping("/matchups/1v1")
	public ResponseEntity<PageMatchupResponse> get1v1Matchup(
		@Parameter(description = "내 챔피언 (기준)", required = true, example = "Ahri")
		@RequestParam("champion1") String champion1,

		@Parameter(description = "상대 챔피언", required = true, example = "Azir")
		@RequestParam("champion2") String champion2,

		@RequestParam(value = "year", required = false) Optional<Integer> year,
		@RequestParam(value = "splits", required = false) Optional<List<String>> splits,
		@RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,
		@RequestParam(value = "teamNames", required = false) Optional<List<String>> teamNames,
		@RequestParam(value = "patch", required = false) Optional<String> patch,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "10") int size) {

		MultiCombinationFilterDto filter = buildFilter(year, splits, leagueNames, teamNames, patch);
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "game.gameDate"));

		PageMatchupResponse response = matchupService.get1v1MatchupStats(champion1, champion2, filter, pageable);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "조합 상세 분석", description = "특정 조합 ID를 기반으로 상세 게임 데이터를 조회합니다.")
	@GetMapping("/{combinationId}/detail")
	public ResponseEntity<CombinationDetailDto> getCombinationDetail(
		@Parameter(description = "조합 고유 ID")
		@PathVariable("combinationId") String combinationId) {

		CombinationDetailDto detail = combinationService.getCombinationDetailById(combinationId);
		return ResponseEntity.ok(detail);
	}

	@Operation(summary = "데이터 업데이트 현황", description = "마지막 데이터 업데이트 시간을 조회합니다.")
	@GetMapping("/stat")
	public ResponseEntity<UpdateInfoDto> getUpdateInfo() {
		return ResponseEntity.ok(combinationService.getUpdateInfo());
	}

	private MultiCombinationFilterDto buildFilter(
		Optional<Integer> year,
		Optional<List<String>> splits,
		Optional<List<String>> leagueNames,
		Optional<List<String>> teamNames,
		Optional<String> patch) {
		return MultiCombinationFilterDto.builder()
			.year(year.orElse(null))
			.splits(splits.orElse(null))
			.leagueNames(leagueNames.orElse(null))
			.teamNames(teamNames.orElse(null))
			.patch(patch.orElse(null))
			.build();
	}
}
