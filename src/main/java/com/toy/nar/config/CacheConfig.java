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

	private static final long TODAY_SCHEDULE_MAX_SIZE = 10;     // 오늘 하루치 캐시는 넉넉하게 10개
	private static final long SCHEDULE_MAX_SIZE = 8192;         // 1MB
	private static final long MATCH_DETAIL_MAX_SIZE = 9892;     // 4MB
	private static final long GAME_RECORD_MAX_SIZE = 10347;    // 6MB

	@Bean
	public CacheManager cacheManager() {
		List<CaffeineCache> caches = List.of(
			createCache("todaySchedules", 1, TimeUnit.HOURS, TODAY_SCHEDULE_MAX_SIZE),
			createCache("dailySchedules", 24, TimeUnit.HOURS, SCHEDULE_MAX_SIZE),
			createCache("matchDetails", 24, TimeUnit.HOURS, MATCH_DETAIL_MAX_SIZE),
			createCache("gameRecords", 24, TimeUnit.HOURS, GAME_RECORD_MAX_SIZE)
		);

		SimpleCacheManager cacheManager = new SimpleCacheManager();
		cacheManager.setCaches(caches);
		return cacheManager;
	}

	private CaffeineCache createCache(String name, int ttl, TimeUnit unit, long maxSize) {
		return new CaffeineCache(name, Caffeine.newBuilder()
			.expireAfterWrite(ttl, unit) // 쓰기 후 만료 시간
			.maximumSize(maxSize)         // 최대 항목 개수
			.recordStats()                // 캐시 통계 수집 (모니터링에 유용)
			.build());
	}
}
