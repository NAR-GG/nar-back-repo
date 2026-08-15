package com.toy.nar.app.mobile.schedule;

import java.time.YearMonth;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;

import lombok.RequiredArgsConstructor;

/**
 * 캘린더·필터 응답 캐시.
 *
 * <p>{@link MobileScheduleService} 는 클래스에 {@code @Transactional(readOnly = true)} 가 걸려 있어
 * 메서드 진입 즉시 커넥션을 잡고 DTO 조립이 끝날 때까지 놓지 않는다. 캐시를 그 서비스 안에 두면
 * 캐시 히트에도 트랜잭션이 먼저 열릴 수 있어(캐시/트랜잭션 어드바이저 순서가 둘 다 기본 우선순위라
 * 보장되지 않는다) 정작 줄이려던 커넥션 점유가 그대로 남는다. 트랜잭션 없는 별도 빈으로 감싸면
 * 히트 시 커넥션을 아예 건드리지 않는다 — {@code ScheduleCacheableService} 와 같은 이유, 같은 모양.</p>
 *
 * <p>실측 2026-08-15 17:12 HLE vs KT 시작: 캘린더 186건이 SQL 40ms 를 쓰려고 커넥션을 357ms 씩
 * (조립 317ms 포함) 점유했고, 필터 101건은 531ms 를 대기했다. 두 엔드포인트가 그 구간 커넥션
 * 대기 시간의 47% 를 차지했다.</p>
 */
@Service
@RequiredArgsConstructor
public class MobileScheduleCacheableService {

	private final MobileScheduleService mobileScheduleService;

	/** 리그·팀·시즌 목록. 로스터 동기화(매일 04:15)와 시즌 전환 때만 바뀐다. */
	@Cacheable(value = "mobileScheduleFilters", key = "#league")
	public MobileScheduleFilterResponse getFilters(String league) {
		return mobileScheduleService.getFilters(league);
	}

	/**
	 * 월별 캘린더. 라이브 상태가 섞이지 않은 경기 목록이라 일정 동기화(30분)에만 바뀐다.
	 * TTL 30초는 컨트롤러가 클라이언트에 약속한 값과 맞춘 것이다.
	 */
	@Cacheable(value = "mobileScheduleCalendar", key = "{#month, #league, #teamId}")
	public MobileScheduleCalendarResponse getCalendar(YearMonth month, List<String> league, List<Long> teamId) {
		return mobileScheduleService.getCalendar(month, league, teamId);
	}
}
