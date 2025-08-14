package com.toy.nar.app.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import com.toy.nar.app.schedule.dto.ScheduleResponseDto;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleCacheableService {

	private final ScheduleFinder scheduleFinder;

	@Cacheable(value = "todaySchedules", key = "#date.toString()")
	public ScheduleResponseDto getTodaySchedule(LocalDate date) {
		log.info("DB에서 오늘 일정 데이터를 조회합니다: {}", date);
		return scheduleFinder.createScheduleResponseDto(date);
	}

	@Cacheable(value = "dailySchedules", key = "#date.toString()")
	public ScheduleResponseDto getPastSchedule(LocalDate date) {
		log.info("DB에서 과거 일정 데이터를 조회합니다: {}", date);
		return scheduleFinder.createScheduleResponseDto(date);
	}
}