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
 *
 * <p>{@code sync = true} 는 이 워크로드에 필수다. 기본 {@code @Cacheable} 은 조회와 저장이
 * 원자적이지 않아, 채우는 데 357ms 가 걸리는 사이 도착한 요청이 전부 같이 미스난다. 유입이
 * 2초에 몰리는 경기 시작에는 첫 버스트가 캐시 없이 그대로 들어가 정작 막으려던 순간을 못 막는다.
 * {@code sync = true} 면 Caffeine 이 키 단위로 원자 계산을 해서 같은 키는 한 번만 실행하고
 * 나머지는 그 결과를 기다린다.</p>
 */
@Service
@RequiredArgsConstructor
public class MobileScheduleCacheableService {

	private final MobileScheduleService mobileScheduleService;

	/** 리그·팀·시즌 목록. 로스터 동기화(매일 04:15)와 시즌 전환 때만 바뀐다. */
	@Cacheable(value = "mobileScheduleFilters", key = "#league", sync = true)
	public MobileScheduleFilterResponse getFilters(String league) {
		return mobileScheduleService.getFilters(league);
	}

	/**
	 * 월별 캘린더. 라이브 상태가 섞이지 않은 경기 목록이라 일정 동기화(30분)에만 바뀐다.
	 * TTL 30초는 컨트롤러가 클라이언트에 약속한 값과 맞춘 것이다.
	 */
	@Cacheable(value = "mobileScheduleCalendar", key = "{#month, #league, #teamId}", sync = true)
	public MobileScheduleCalendarResponse getCalendar(YearMonth month, List<String> league, List<Long> teamId) {
		return mobileScheduleService.getCalendar(month, league, teamId);
	}
}
