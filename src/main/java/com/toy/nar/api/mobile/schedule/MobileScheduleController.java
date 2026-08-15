package com.toy.nar.api.mobile.schedule;

import com.toy.nar.app.mobile.schedule.MobileScheduleCacheableService;
import com.toy.nar.app.mobile.schedule.MobileScheduleService;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "Mobile. 경기 일정", description = "모바일 앱 경기일정/경기리스트 전용 API")
@RestController
@RequestMapping("/api/mobile/schedules")
@RequiredArgsConstructor
public class MobileScheduleController {

	private final MobileScheduleService mobileScheduleService;
	private final MobileScheduleCacheableService mobileScheduleCacheableService;

	@Operation(summary = "모바일 일정 필터 조회", description = "모바일 경기일정/경기리스트 화면의 리그와 팀 필터 옵션을 조회합니다.")
	@GetMapping("/filters")
	public ResponseEntity<MobileScheduleFilterResponse> getFilters(
			@Parameter(description = "팀 옵션을 조회할 리그 (전체는 ALL)", example = "LCK")
			@RequestParam(defaultValue = "LCK") String league) {
		// 리그·팀·시즌 목록은 로스터 변동/시즌 전환 때만 바뀌므로 1시간 캐시.
		// Cache-Control 만으로는 부족하다 — 앱 HTTP 클라이언트(dart:io)가 응답 캐시를 하지 않아
		// 실측 101건이 전부 서버까지 들어왔다. 서버 캐시가 실효 방어선이다.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
				.body(mobileScheduleCacheableService.getFilters(league));
	}

	@Operation(summary = "모바일 월별 캘린더 조회", description = "월별 캘린더 마킹용 경기 날짜와 경기 수를 조회합니다.")
	@GetMapping("/calendar")
	public ResponseEntity<MobileScheduleCalendarResponse> getCalendar(
			@Parameter(description = "조회 월", example = "2026-04")
			@RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@Parameter(description = "리그 (전체는 ALL). 복수 지정 가능: league=LCK&league=LEC", example = "LCK")
			@RequestParam(defaultValue = "LCK") List<String> league,
			@Parameter(description = "팀 ID. 복수 지정 가능: teamId=1&teamId=3", example = "1")
			@RequestParam(required = false) List<Long> teamId) {
		// 앱 첫 화면이 진입 즉시 부르는 응답. 마킹용 날짜/경기 수라 30초 지연은 눈에 띄지 않는다.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)))
				.body(mobileScheduleCacheableService.getCalendar(month, league, teamId));
	}

	@Operation(summary = "모바일 일별 경기 리스트 조회", description = "선택 날짜의 모바일 경기 리스트 카드 데이터를 조회합니다.")
	@GetMapping
	public ResponseEntity<MobileScheduleListResponse> getDailySchedules(
			@Parameter(description = "조회 일자", example = "2026-04-01")
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Parameter(description = "리그 (전체는 ALL). 복수 지정 가능: league=LCK&league=KESPA", example = "LCK")
			@RequestParam(defaultValue = "LCK") List<String> league,
			@Parameter(description = "팀 ID. 복수 지정 가능: teamId=1&teamId=5", example = "1")
			@RequestParam(required = false) List<Long> teamId) {
		return ResponseEntity.ok(mobileScheduleService.getDailySchedules(date, league, teamId));
	}
}
