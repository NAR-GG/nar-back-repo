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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

	private final ScheduleService scheduleService;

	@GetMapping("")
	public ResponseEntity<ScheduleResponseDto> getDailySchedule(
		@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		ScheduleResponseDto schedule = scheduleService.getDailySchedule(date);
		return ResponseEntity.ok(schedule);
	}

	@GetMapping("/matches/{matchId}/detail")
	public ResponseEntity<MatchDetailResponseDto> getMatchDetail(
		@PathVariable String matchId
	) {
		MatchDetailResponseDto detail = scheduleService.getMatchDetail(matchId);
		return ResponseEntity.ok(detail);
	}
}
