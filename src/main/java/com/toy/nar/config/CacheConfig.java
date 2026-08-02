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
			createCacheWithTTL("adminStatsOverview", 1, TimeUnit.MINUTES, ADMIN_STATS_MAX_SIZE)
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