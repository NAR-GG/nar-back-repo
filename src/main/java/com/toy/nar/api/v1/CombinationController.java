// combination/CombinationController.java
package com.toy.nar.api.v1;

import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.CombinationFilterDto;
import com.toy.nar.app.analysis.dto.CombinationResponseDto;
import com.toy.nar.app.analysis.dto.CombinationStatDto;
import com.toy.nar.app.analysis.dto.PageCombinationResponse;
import com.toy.nar.app.analysis.dto.UpdateInfoDto;
import com.toy.nar.app.analysis.service.CombinationService;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

	@GetMapping("/v2")
	public ResponseEntity<PageCombinationResponse> getCombinationsV2(
		@RequestParam List<String> champions,
		@RequestParam Optional<Integer> year,
		@RequestParam Optional<List<String>> splits,
		@RequestParam Optional<List<String>> leagueNames,
		@RequestParam Optional<List<String>> teamNames,
		@RequestParam Optional<String> patch,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(defaultValue = "frequency") String sort) {  // 추가: 정렬 타입 (frequency, recency, patch)

		MultiCombinationFilterDto filter = MultiCombinationFilterDto.builder()
			.year(year.orElse(null))
			.splits(splits.orElse(null))
			.leagueNames(leagueNames.orElse(null))
			.teamNames(teamNames.orElse(null))
			.patch(patch.orElse(null))
			.build();

		Pageable basicPageable = PageRequest.of(page, size);
		Pageable pageable = combinationService.applyDynamicSort(basicPageable, sort);

		PageCombinationResponse combinations = combinationService.findTopCombinationsV2(champions, filter, pageable);
		return ResponseEntity.ok(combinations);
	}

	// 🔥 새로운 상세정보 엔드포인트: ID 기반 조회
	@GetMapping("/{combinationId}/detail")
	public ResponseEntity<CombinationDetailDto> getCombinationDetail(
		@PathVariable String combinationId) {

		CombinationDetailDto detail = combinationService.getCombinationDetailById(combinationId);
		return ResponseEntity.ok(detail);
	}

	// 🔥 기존 상세정보 엔드포인트 유지 (하위 호환성)
	@GetMapping("/detail")
	public ResponseEntity<CombinationDetailDto> getCombinationDetailLegacy(
		@RequestParam List<String> champions,
		@RequestParam Optional<Integer> year,
		@RequestParam Optional<String> split,
		@RequestParam Optional<String> leagueName,
		@RequestParam Optional<String> teamName,
		@RequestParam Optional<String> patch) {

		CombinationFilterDto filter = CombinationFilterDto.builder()
			.year(year.orElse(null))
			.split(split.orElse(null))
			.leagueName(leagueName.orElse(null))
			.teamName(teamName.orElse(null))
			.patch(patch.orElse(null))
			.build();

		CombinationDetailDto detail = combinationService.getCombinationDetail(champions, filter);
		return ResponseEntity.ok(detail);
	}

	@GetMapping("/stat")
	public ResponseEntity<UpdateInfoDto> getUpdateInfo() {
		return ResponseEntity.ok(combinationService.getUpdateInfo());
	}

}
