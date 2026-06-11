package com.toy.nar.api.mobile.match;

import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchGamesResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 경기 리스트", description = "모바일 경기 리스트 무한 스크롤 및 세트(게임) 조회 전용 API")
@RestController
@RequestMapping("/api/mobile/matches")
@RequiredArgsConstructor
public class MobileMatchController {

	private final MobileScheduleService mobileScheduleService;

	@Operation(
			summary = "모바일 경기 리스트 커서 페이지 조회",
			description = "최신 경기부터 과거 방향으로 커서 기반 페이지네이션 조회합니다. "
					+ "첫 페이지는 cursor 없이 호출하고, 이후 응답의 nextCursor를 cursor로 전달하면 이어서 조회됩니다. "
					+ "각 경기에는 세트(games) 식별자 목록이 포함됩니다.")
	@GetMapping
	public ResponseEntity<MobileMatchPageResponse> getMatches(
			@Parameter(description = "리그", example = "LCK")
			@RequestParam(defaultValue = "LCK") String league,
			@Parameter(description = "팀 ID", example = "1")
			@RequestParam(required = false) Long teamId,
			@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략")
			@RequestParam(required = false) String cursor,
			@Parameter(description = "페이지 크기(1~50)", example = "20")
			@RequestParam(defaultValue = "20") Integer size) {
		return ResponseEntity.ok(mobileScheduleService.getMatchPage(league, teamId, cursor, size));
	}

	@Operation(
			summary = "매치 세트(게임) 목록 조회",
			description = "matchId로 해당 매치의 세트 목록을 조회합니다. "
					+ "gameId는 라이브/선수 평점 API용, recordGameId는 기록(record) API용 식별자입니다.")
	@GetMapping("/{matchId}/games")
	public ResponseEntity<MobileMatchGamesResponse> getMatchGames(
			@Parameter(description = "매치 ID", example = "113990000000000001")
			@PathVariable String matchId) {
		return ResponseEntity.ok(mobileScheduleService.getMatchGames(matchId));
	}
}
