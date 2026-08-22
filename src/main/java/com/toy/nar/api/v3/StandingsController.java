package com.toy.nar.api.v3;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.standings.StandingsService;
import com.toy.nar.app.standings.dto.StandingsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "9. Standings API", description = "리그 순위표")
@RestController
@RequestMapping("/api/standings")
@RequiredArgsConstructor
public class StandingsController {

	private final StandingsService standingsService;

	@Operation(summary = "리그 순위표 조회",
			description = "리그의 정규 스테이지 순위를 반환합니다. "
					+ "그룹이 없는 리그도 groups 길이 1 로 내려갑니다. "
					+ "순위표가 없는 대회(스위스·토너먼트)는 supported=false 입니다.")
	@GetMapping
	public StandingsResponse getStandings(
			@Parameter(description = "리그 이름", example = "LCK")
			@RequestParam String league) {
		return standingsService.getStandings(league);
	}
}
