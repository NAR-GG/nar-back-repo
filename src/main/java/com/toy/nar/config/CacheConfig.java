package com.toy.nar.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("dailySchedules"); // @Cacheable의 value와 동일한 이름 등록
		cacheManager.setCaffeine(Caffeine.newBuilder()
			// 캐시가 쓰여진 후 10분이 지나면 자동으로 삭제됩니다.
			.expireAfterWrite(10, TimeUnit.MINUTES)
			// 캐시는 최대 100개까지만 저장합니다. (메모리 관리)
			.maximumSize(100)
		);
		return cacheManager;
	}
}
