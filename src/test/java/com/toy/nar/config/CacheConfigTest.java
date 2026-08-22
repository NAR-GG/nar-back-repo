package com.toy.nar.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

/**
 * 스케줄러를 별도 파드로 뗀 뒤로 지켜야 하는 불변조건 하나를 잠근다.
 *
 * <p>Caffeine 은 JVM 안에 있다. 파드가 둘이라 {@code CacheEvictionService} 의 evict 는
 * 스케줄러 JVM 의 캐시만 지우고 웹 파드의 캐시는 건드리지 못한다. 그래서 evict 로
 * 신선도를 지키던 캐시는 반드시 만료가 있어야 한다 — 없으면 웹 파드가 재시작할 때까지
 * 영원히 낡은 값을 준다. 조용히 깨지는 종류라 사람이 눈치채기 어렵다.
 */
class CacheConfigTest {

	/** CacheEvictionService.evictScheduleCaches() 가 지우는 캐시들. */
	private static final List<String> EVICTED_CACHES = List.of(
			"todaySchedules", "dailySchedules", "todayMatchDetails", "matchDetails");

	private final CacheManager cacheManager = initialized(new CacheConfig().cacheManager());

	/** SimpleCacheManager 는 InitializingBean 이다. 스프링 밖에서 만들면 직접 초기화해야 캐시가 들어찬다. */
	private static CacheManager initialized(CacheManager cacheManager) {
		((SimpleCacheManager) cacheManager).afterPropertiesSet();
		return cacheManager;
	}

	@Test
	@DisplayName("evict 대상 캐시는 전부 만료가 있다 — 파드가 둘이라 evict 만으로는 못 지운다")
	void evictedCachesMustExpire() {
		for (String name : EVICTED_CACHES) {
			CaffeineCache cache = (CaffeineCache) cacheManager.getCache(name);
			assertThat(cache).as("%s 캐시가 없다", name).isNotNull();
			assertThat(cache.getNativeCache().policy().expireAfterWrite())
					.as("%s 는 evict 대상이라 TTL 이 있어야 한다", name)
					.isPresent();
		}
	}

	@Test
	@DisplayName("오늘 경기 캐시는 1분 이하다 — 진행 중 경기의 세트 스코어가 여기 실린다")
	void todayCachesStayFresh() {
		for (String name : List.of("todaySchedules", "todayMatchDetails")) {
			CaffeineCache cache = (CaffeineCache) cacheManager.getCache(name);
			assertThat(cache.getNativeCache().policy().expireAfterWrite())
					.get()
					.satisfies(policy -> assertThat(policy.getExpiresAfter().toSeconds())
							.as("%s TTL", name)
							.isLessThanOrEqualTo(60));
		}
	}
}
