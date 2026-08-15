package com.toy.nar.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

	private static final long TODAY_SCHEDULE_MAX_SIZE = 2;
	private static final long TODAY_MATCH_DETAIL_MAX_SIZE = 10;
	private static final long SCHEDULE_MAX_SIZE = 8192;    // 1MB
	private static final long MATCH_DETAIL_MAX_SIZE = 9892; // 4MB
	private static final long GAME_RECORD_MAX_SIZE = 10347; // 6MB
	private static final long ADMIN_STATS_MAX_SIZE = 16;   // 기간(days) 조합 몇 개뿐
	private static final long MOBILE_SCHEDULE_FILTER_MAX_SIZE = 16;  // 리그 코드 수만큼
	// 키가 (월 × 리그 조합 × 팀 조합)이라 팀 필터를 자유롭게 섞으면 이론상 무한하다.
	// TTL 이 30초라 실제로 살아 있는 건 그 30초 안에 조회된 조합뿐이고(버스트 때 수십 개 수준),
	// 이 값은 악의적/비정상 조합이 힙을 먹지 않게 막는 상한이다.
	private static final long MOBILE_SCHEDULE_CALENDAR_MAX_SIZE = 256;

	@Bean
	public CacheManager cacheManager() {
		List<CaffeineCache> caches = List.of(
			// 변할 가능성이 있는 데이터 → TTL 유지
			createCacheWithTTL("todaySchedules", 1, TimeUnit.HOURS, TODAY_SCHEDULE_MAX_SIZE),
			createCacheWithTTL("todayMatchDetails", 1, TimeUnit.HOURS, TODAY_MATCH_DETAIL_MAX_SIZE),

			// 불변 데이터 → TTL 제거, size limit만 적용 (LRU 정책)
			createCacheNoTTL("dailySchedules", SCHEDULE_MAX_SIZE),
			createCacheNoTTL("matchDetails", MATCH_DETAIL_MAX_SIZE),
			createCacheNoTTL("gameRecords", GAME_RECORD_MAX_SIZE),

			// 백오피스 대시보드 집계. 알림 시계열이 35만 행 스캔이라 매 로드마다 초 단위로 걸린다.
			// 운영 지표라 1분 지연은 무해 → 첫 로드만 값을 치르고 기간 토글·다른 관리자는 캐시 히트.
			createCacheWithTTL("adminStatsSeries", 1, TimeUnit.MINUTES, ADMIN_STATS_MAX_SIZE),
			createCacheWithTTL("adminStatsNotifications", 1, TimeUnit.MINUTES, ADMIN_STATS_MAX_SIZE),
			createCacheWithTTL("adminStatsOverview", 1, TimeUnit.MINUTES, ADMIN_STATS_MAX_SIZE),

			// 앱 첫 화면이 진입 즉시 부르는 두 응답. 경기 시작 순간 유입이 몰리면 이 둘이
			// 커넥션을 오래 잡아 나머지 요청까지 풀에서 대기시킨다(MobileScheduleCacheableService).
			// TTL 은 컨트롤러가 클라이언트에 내리는 Cache-Control 값과 맞췄다.
			createCacheWithTTL("mobileScheduleFilters", 1, TimeUnit.HOURS, MOBILE_SCHEDULE_FILTER_MAX_SIZE),
			createCacheWithTTL("mobileScheduleCalendar", 30, TimeUnit.SECONDS, MOBILE_SCHEDULE_CALENDAR_MAX_SIZE)
		);

		SimpleCacheManager cacheManager = new SimpleCacheManager();
		cacheManager.setCaches(caches);
		return cacheManager;
	}

	private CaffeineCache createCacheWithTTL(String name, int ttl, TimeUnit unit, long maxSize) {
		return new CaffeineCache(name, Caffeine.newBuilder()
			.expireAfterWrite(ttl, unit)
			.maximumSize(maxSize)
			.recordStats()
			.build());
	}

	private CaffeineCache createCacheNoTTL(String name, long maxSize) {
		return new CaffeineCache(name, Caffeine.newBuilder()
			.maximumSize(maxSize)
			.recordStats()
			.build());
	}
}