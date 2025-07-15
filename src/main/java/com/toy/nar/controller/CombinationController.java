package com.toy.nar.controller;

import com.toy.nar.dto.CombinationFilterDto;
import com.toy.nar.dto.CombinationStatDto;
import com.toy.nar.service.CombinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/combinations")
@RequiredArgsConstructor
public class CombinationController {

	private final CombinationService combinationService;

	@GetMapping
	public ResponseEntity<List<CombinationStatDto>> getCombinations(
		@RequestParam List<String> champions,
		// 필터 파라미터들은 선택 사항(Optional)으로 받습니다.
		@RequestParam Optional<Integer> year,
		@RequestParam Optional<String> split,
		@RequestParam Optional<String> leagueName,
		@RequestParam Optional<String> teamName) {

		// 필터 DTO 생성
		CombinationFilterDto filter = CombinationFilterDto.builder()
			.year(year.orElse(null))
			.split(split.orElse(null))
			.leagueName(leagueName.orElse(null))
			.teamName(teamName.orElse(null))
			.build();

		// 서비스 호출 및 결과 반환
		List<CombinationStatDto> topCombinations = combinationService.findTopCombinations(champions, filter);
		return ResponseEntity.ok(topCombinations);
	}
}