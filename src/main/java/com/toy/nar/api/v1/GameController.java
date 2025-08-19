package com.toy.nar.api.v1;

import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.app.data.game.dto.GameResponseDto;
import com.toy.nar.app.data.game.service.GameService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

	private final GameService gameService;

	@GetMapping
	public ResponseEntity<Page<GameResponseDto>> getRecentGames(
		@RequestParam(value = "leagueNames", required = false) List<String> leagueNames,
		@RequestParam(value = "splits", required = false) List<String> splits,
		@RequestParam(value = "teamNames", required = false) List<String> teamNames,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(defaultValue = "DESC") String sort) {

		MultiCombinationFilterDto filter = MultiCombinationFilterDto.builder()
			.leagueNames(leagueNames)
			.splits(splits)
			.teamNames(teamNames)
			.build();

		// 프론트의 'sort' 파라미터를 Spring Data JPA의 Sort 객체로 변환
		Sort.Direction direction = "ASC".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "actualGameStartTime"));

		Page<GameResponseDto> gamePage = gameService.findRecentGames(filter, pageable);

		return ResponseEntity.ok(gamePage);
	}
}
