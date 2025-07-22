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
		@RequestParam("champions") List<String> champions,
		@RequestParam(value = "year", required = false) Optional<Integer> year,
		@RequestParam(value = "splits", required = false) Optional<List<String>> splits,
		@RequestParam(value = "leagueNames", required = false) Optional<List<String>> leagueNames,
		@RequestParam(value = "teamNames", required = false) Optional<List<String>> teamNames,
		@RequestParam(value = "patch", required = false) Optional<String> patch,
		@RequestParam(value = "page", defaultValue = "0") int page,
		@RequestParam(value = "size", defaultValue = "10") int size,
		@RequestParam(value = "sort", defaultValue = "frequency") String sort) {  // 추가: 정렬 타입 (frequency, recency, patch)

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

	@GetMapping("/{combinationId}/detail")
	public ResponseEntity<CombinationDetailDto> getCombinationDetail(
		@PathVariable("combinationId") String combinationId) {

		CombinationDetailDto detail = combinationService.getCombinationDetailById(combinationId);
		return ResponseEntity.ok(detail);
	}

	@GetMapping("/stat")
	public ResponseEntity<UpdateInfoDto> getUpdateInfo() {
		return ResponseEntity.ok(combinationService.getUpdateInfo());
	}

}
