package com.toy.nar.app.lolesports;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

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
