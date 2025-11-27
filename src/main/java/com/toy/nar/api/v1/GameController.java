package com.toy.nar.api.v1;

import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.app.data.game.dto.GameResponseDto;
import com.toy.nar.app.data.game.service.GameService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "3. 게임 목록 조회", description = "전체 게임 일정 및 결과 목록을 필터링하여 조회합니다.")
@Validated
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

	private final GameService gameService;

	@Operation(summary = "최근 게임 목록 검색", description = "리그, 시즌, 팀 필터를 적용하여 최근 게임 목록을 페이징 처리해 반환합니다.")
	@GetMapping
	public ResponseEntity<Page<GameResponseDto>> getRecentGames(
		@RequestParam(value = "leagueNames", required = false) List<String> leagueNames,
		@RequestParam(value = "splits", required = false) List<String> splits,
		@RequestParam(value = "teamNames", required = false) List<String> teamNames,
		@Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
		@RequestParam(defaultValue = "0") int page,
		@Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
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
