package com.toy.nar.api.v1;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.schedule.ScheduleService;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "2. 경기 일정", description = "날짜별 경기 일정 및 매치 상세 정보를 조회합니다.")
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

	private final ScheduleService scheduleService;

	@Operation(summary = "일별 경기 일정 조회", description = "특정 날짜의 경기 목록을 조회합니다. (캘린더 클릭 시 사용)")
	@GetMapping("")
	public ResponseEntity<ScheduleResponseDto> getDailySchedule(
		@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		ScheduleResponseDto schedule = scheduleService.getDailySchedule(date);
		return ResponseEntity.ok(schedule);
	}

	@Operation(summary = "매치 상세 정보 조회", description = "특정 매치(Match)의 세부 정보를 조회합니다.")
	@GetMapping("/matches/{matchId}/detail")
	public ResponseEntity<MatchDetailResponseDto> getMatchDetail(
		@PathVariable String matchId
	) {
		MatchDetailResponseDto detail = scheduleService.getMatchDetail(matchId);
		return ResponseEntity.ok(detail);
	}
}
