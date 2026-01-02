package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

@Hidden
@RestController
@RequiredArgsConstructor
public class WorldsController {

	private final com.toy.nar.app.lolesports.LeagueMatchService leagueMatchService;

	@GetMapping("/api/worlds/recent")
	public List<MatchResultDto> getRecentMatches(@RequestParam(required = false, defaultValue = "LCK") String league) {
		return leagueMatchService.getRecentMatchesFromDb(league);
	}

	@GetMapping("/api/matches")
	public ResponseEntity<MatchResponseWrapper> getWorldsMatches(
		@RequestParam(required = false) String pageToken,
		@RequestParam(required = false) String league,
		@RequestParam(required = false) String date) {

		// DB 조회 방식 (날짜 필터 추가)
		MatchResponseWrapper response = leagueMatchService.getMatchesFromDb(league, date);
		return ResponseEntity.ok(response);
	}
}
