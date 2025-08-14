package com.toy.nar.api.admin;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Profile("local") // ❗️ local 프로필에서만 활성화되어 운영 환경에 노출되지 않도록 함
@RestController
@RequiredArgsConstructor
public class DebugController {

	private final CacheManager cacheManager;

	@GetMapping("/api/debug/caches")
	public ResponseEntity<Map<String, Object>> getCacheDetails() {
		Map<String, Object> result = new HashMap<>();

		// CacheManager에 등록된 모든 캐시의 이름을 가져옵니다.
		for (String cacheName : cacheManager.getCacheNames()) {
			CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
			if (caffeineCache != null) {
				// 네이티브 Caffeine 캐시 객체를 가져옵니다.
				Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

				// 캐시에 대한 상세 정보를 Map에 담습니다.
				Map<String, Object> cacheDetails = Map.of(
					"size", nativeCache.estimatedSize(), // 현재 캐시된 아이템 수
					"keys", nativeCache.asMap().keySet(),    // 캐시에 저장된 모든 키 목록
					"stats", nativeCache.stats().toString()  // 히트, 미스 등 통계 정보
				);
				result.put(cacheName, cacheDetails);
			}
		}

		return ResponseEntity.ok(result);
	}
}