package com.toy.nar.api.v1;

import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.PageCombinationResponse;
import com.toy.nar.app.analysis.dto.PageMatchupResponse;
import com.toy.nar.app.analysis.dto.UpdateInfoDto;
import com.toy.nar.app.analysis.service.CombinationService;
import com.toy.nar.app.analysis.service.MatchupService;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;

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

@RestController
@RequestMapping("/api/combinations")
@RequiredArgsConstructor
public class CombinationController {

	private final CombinationService combinationService;
	private final MatchupService matchupService;

	/**
	 * 챔피언 조합 조회 엔드포인트.
	 * 역할: 요청 파라미터 처리 및 CombinationService와 협력.
	 */
	@GetMapping("/")
	public ResponseEntity<PageCombinationResponse> getCombinationsV2(
		@RequestParam("champions") List<String> champions,
		@RequestParam(value = "year", required = false) Optional<Integer> year,
		@RequestParam(value = "splits", required = false) Optional<List<String>> splits,
		@RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,
		@RequestParam(value = "teamNames", required = false) Optional<List<String>> teamNames,
		@RequestParam(value = "patch", required = false) Optional<String> patch,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "10") int size,
		@RequestParam(value = "sort", defaultValue = "frequency") String sort) {

		MultiCombinationFilterDto filter = buildFilter(year, splits, leagueNames, teamNames, patch);

		Pageable basicPageable = PageRequest.of(page, size);
		Pageable pageable = combinationService.applyDynamicSort(basicPageable, sort);

		PageCombinationResponse combinations = combinationService.findTopCombinationsV2(champions, filter, pageable);
		return ResponseEntity.ok(combinations);
	}

	/**
	 * 1v1 매치업 조회 엔드포인트.
	 * 역할: 요청 파라미터 처리 및 MatchupService와 협력 (MatchupService로 책임 이전).
	 * 변경: combinationService 대신 matchupService 호출, 반환 타입 PageMatchupResponse로 일치.
	 */
	@GetMapping("/matchups/1v1")
	public ResponseEntity<PageMatchupResponse> get1v1Matchup(
		@RequestParam("champion1") String champion1,
		@RequestParam("champion2") String champion2,
		@RequestParam(value = "year", required = false) Optional<Integer> year,
		@RequestParam(value = "splits", required = false) Optional<List<String>> splits,
		@RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,
		@RequestParam(value = "teamNames", required = false) Optional<List<String>> teamNames,
		@RequestParam(value = "patch", required = false) Optional<String> patch,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "10") int size) {

		MultiCombinationFilterDto filter = buildFilter(year, splits, leagueNames, teamNames, patch);

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "game.gameDate"));  // 최신순 정렬

		PageMatchupResponse response = matchupService.get1v1MatchupStats(champion1, champion2, filter, pageable);
		return ResponseEntity.ok(response);
	}

	/**
	 * 조합 상세 조회 엔드포인트.
	 * 역할: ID 기반 조회, CombinationService와 협력.
	 */
	@GetMapping("/{combinationId}/detail")
	public ResponseEntity<CombinationDetailDto> getCombinationDetail(
		@PathVariable("combinationId") String combinationId) {

		CombinationDetailDto detail = combinationService.getCombinationDetailById(combinationId);
		return ResponseEntity.ok(detail);
	}

	/**
	 * 업데이트 정보 조회 엔드포인트.
	 * 역할: 간단 조회, CombinationService와 협력.
	 */
	@GetMapping("/stat")
	public ResponseEntity<UpdateInfoDto> getUpdateInfo() {
		return ResponseEntity.ok(combinationService.getUpdateInfo());
	}

	// --- Private Helper Method (공통 책임 분리: 필터 빌드) ---

	/**
	 * 필터 객체 생성 헬퍼: 중복 코드 제거 및 재사용성 향상.
	 */
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
