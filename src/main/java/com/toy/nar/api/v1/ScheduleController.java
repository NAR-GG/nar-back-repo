package com.toy.nar.api.v1;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.schedule.ScheduleService;
import com.toy.nar.app.schedule.dto.ScheduleCalendarResponseDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.config.swagger.ApiErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "2. 경기 일정", description = "날짜별 경기 일정 및 매치 상세 정보를 조회합니다.")
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

	private final ScheduleService scheduleService;
	private final LeagueMatchService leagueMatchService;

	@Operation(summary = "일별 경기 일정 조회", description = "특정 날짜의 경기 목록을 조회합니다. (캘린더 클릭 시 사용)")
	@ApiErrorCode(ErrorCode.INVALID_INPUT_VALUE)
	@GetMapping("")
	public ResponseEntity<ScheduleResponseDto> getDailySchedule(
			@Parameter(description = "조회 일자 (예: 2026-04-01)", example = "2026-04-01")
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		ScheduleResponseDto schedule = scheduleService.getDailySchedule(date);
		return ResponseEntity.ok(schedule);
	}

	@Operation(summary = "월별 경기 존재 날짜 조회", description = "특정 월에 경기가 있는 날짜와 경기 수를 조회합니다. (달력 마킹용)")
	@ApiErrorCode(ErrorCode.INVALID_INPUT_VALUE)
	@GetMapping("/calendar")
	public ResponseEntity<ScheduleCalendarResponseDto> getMonthlyScheduleCalendar(
			@Parameter(description = "조회 월 (예: 2026-04)", example = "2026-04")
			@RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@Parameter(description = "리그 필터 (예: LCK)", example = "LCK")
			@RequestParam(required = false) String league,
			@Parameter(description = "팀 ID", example = "1")
			@RequestParam(required = false) Long teamId) {
		ScheduleCalendarResponseDto calendar = scheduleService.getMonthlyScheduleCalendar(month, league, teamId);
		return ResponseEntity.ok(calendar);
	}

	@Operation(summary = "매치 상세 정보 조회", description = "특정 매치(Match)의 세부 정보를 조회합니다.")
	@ApiErrorCode({ ErrorCode.INVALID_MATCH_ID, ErrorCode.MATCH_NOT_FOUND })
	@GetMapping("/matches/{matchId}/detail")
	public ResponseEntity<MatchDetailResponseDto> getMatchDetail(
			@PathVariable String matchId) {
		MatchDetailResponseDto detail = scheduleService.getMatchDetail(matchId);
		return ResponseEntity.ok(detail);
	}

	@Operation(summary = "과거 경기 이력 수동 동기화", description = "Lolesports의 모든 대상 리그에 대해 과거 경기 이력을 수동으로 동기화합니다.")
	@org.springframework.web.bind.annotation.PostMapping("/sync/history")
	public ResponseEntity<String> syncHistory() {
		int count = leagueMatchService.syncAllLeaguesFullHistory();
		return ResponseEntity.ok("Synced " + count + " matches.");
	}
}
