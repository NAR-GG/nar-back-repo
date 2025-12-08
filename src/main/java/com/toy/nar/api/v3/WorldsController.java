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

	private final WorldsService worldsService;

	@GetMapping("/api/worlds/recent")
	public List<MatchResultDto> getRecentMatches() {
		return worldsService.getRecent3Matches();
	}

	@GetMapping("/api/matches")
	public ResponseEntity<MatchResponseWrapper> getWorldsMatches(
		@RequestParam(required = false) String pageToken) {

		MatchResponseWrapper response = worldsService.getWorldsMatches(pageToken);
		return ResponseEntity.ok(response);
	}
}
