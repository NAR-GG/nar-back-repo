package com.toy.nar.app.mobile.schedule;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.config.CacheConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 캐시가 실제로 위임 서비스 호출을 막는지 확인한다. 여기서 막지 못하면
 * {@link MobileScheduleService} 의 {@code @Transactional} 이 열려 커넥션을 잡는다 —
 * 이 PR 이 없애려던 그 점유다. 캐시 이름이 {@link CacheConfig} 에 등록돼 있는지도 같이 잡힌다
 * (SimpleCacheManager 는 미등록 이름에 예외를 던진다).
 */
class MobileScheduleCacheableServiceTest {

	private AnnotationConfigApplicationContext context;
	private MobileScheduleService delegate;
	private MobileScheduleCacheableService cacheable;

	@Configuration
	@EnableCaching
	static class TestConfig {

		@Bean
		MobileScheduleService mobileScheduleService() {
			return mock(MobileScheduleService.class);
		}

		@Bean
		MobileScheduleCacheableService mobileScheduleCacheableService(MobileScheduleService service) {
			return new MobileScheduleCacheableService(service);
		}
	}

	@BeforeEach
	void setUp() {
		context = new AnnotationConfigApplicationContext(CacheConfig.class, TestConfig.class);
		delegate = context.getBean(MobileScheduleService.class);
		cacheable = context.getBean(MobileScheduleCacheableService.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	@Test
	void filtersHitDelegateOncePerLeague() {
		when(delegate.getFilters(any())).thenReturn(filterResponse());

		cacheable.getFilters("LCK");
		cacheable.getFilters("LCK");
		cacheable.getFilters("LEC");

		verify(delegate, times(1)).getFilters("LCK");
		verify(delegate, times(1)).getFilters("LEC");
	}

	@Test
	void calendarKeyCoversMonthLeagueAndTeam() {
		when(delegate.getCalendar(any(), any(), any())).thenReturn(calendarResponse());

		YearMonth august = YearMonth.of(2026, 8);
		cacheable.getCalendar(august, List.of("LCK"), null);
		cacheable.getCalendar(august, List.of("LCK"), null);

		verify(delegate, times(1)).getCalendar(august, List.of("LCK"), null);

		// 팀 필터가 다르면 다른 응답이라 캐시를 공유하면 안 된다.
		cacheable.getCalendar(august, List.of("LCK"), List.of(1L));
		verify(delegate, times(1)).getCalendar(august, List.of("LCK"), List.of(1L));

		// 월이 다르면 마찬가지.
		cacheable.getCalendar(YearMonth.of(2026, 9), List.of("LCK"), null);
		verify(delegate, times(1)).getCalendar(YearMonth.of(2026, 9), List.of("LCK"), null);
	}

	@Test
	void cachedResponseIsReturnedAsIs() {
		MobileScheduleFilterResponse first = filterResponse();
		when(delegate.getFilters("LCK")).thenReturn(first);

		assertThat(cacheable.getFilters("LCK")).isSameAs(first);
		assertThat(cacheable.getFilters("LCK")).isSameAs(first);
	}

	private MobileScheduleFilterResponse filterResponse() {
		return new MobileScheduleFilterResponse("LCK", List.of(), List.of(), List.of());
	}

	private MobileScheduleCalendarResponse calendarResponse() {
		return new MobileScheduleCalendarResponse("2026-08", "LCK", null, List.of());
	}
}
