package com.toy.nar.api.mobile.match;

import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchGamesResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchPageResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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
					+ "각 경기에는 세트(games) 식별자 목록이 포함됩니다. "
					+ "시즌 필터 옵션은 /api/mobile/schedules/filters 응답의 seasons에서 가져옵니다.")
	@GetMapping
	public ResponseEntity<MobileMatchPageResponse> getMatches(
			@Parameter(description = "리그 (전체는 ALL)", example = "LCK")
			@RequestParam(defaultValue = "LCK") String league,
			@Parameter(description = "팀 ID", example = "1")
			@RequestParam(required = false) Long teamId,
			@Parameter(description = "시즌 연도", example = "2026")
			@RequestParam(required = false) Integer seasonYear,
			@Parameter(description = "스플릿 (filters의 seasons.split 값)", example = "Spring")
			@RequestParam(required = false) String split,
			@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략")
			@RequestParam(required = false) String cursor,
			@Parameter(description = "페이지 크기(1~50)", example = "20")
			@RequestParam(defaultValue = "20") Integer size) {
		return ResponseEntity.ok(mobileScheduleService.getMatchPage(league, teamId, seasonYear, split, cursor, size));
	}

	@Operation(
			summary = "매치 단건 조회",
			description = "matchId로 경기 한 건을 조회합니다. 응답은 /api/mobile/schedules 의 "
					+ "matches 배열 항목과 동일한 형태입니다. 푸시 알림 딥링크 진입 시 경기 정보를 채우는 용도입니다.")
	@GetMapping("/{matchId}")
	public ResponseEntity<MobileScheduleListResponse.MobileMatchSummary> getMatch(
			@Parameter(description = "매치 ID", example = "113990000000000001")
			@PathVariable String matchId) {
		return ResponseEntity.ok(mobileScheduleService.getMatch(matchId));
	}

	@Operation(
			summary = "매치 세트(게임) 목록 조회",
			description = "matchId로 해당 매치의 세트 목록을 조회합니다. "
					+ "gameId는 라이브/선수 평점 API용, recordGameId는 기록(record) API용 식별자입니다.")
	@GetMapping("/{matchId}/games")
	public ResponseEntity<MobileMatchGamesResponse> getMatchGames(
			@Parameter(description = "매치 ID", example = "113990000000000001")
			@PathVariable String matchId) {
		// 종료 경기는 불변이지만 진행 중 경기도 같은 엔드포인트를 쓴다. 5분을 주면 다음 세트가
		// 시작돼도 앱에 5분 늦게 보이므로 30초로 제한한다.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)))
				.body(mobileScheduleService.getMatchGames(matchId));
	}
}
