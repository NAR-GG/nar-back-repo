package com.toy.nar.app.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheEvictionService {

	@Caching(evict = {
			@CacheEvict(value = "todaySchedules", allEntries = true),
			@CacheEvict(value = "dailySchedules", allEntries = true),
			@CacheEvict(value = "todayMatchDetails", allEntries = true),
			@CacheEvict(value = "matchDetails", allEntries = true)
	})
	public void evictScheduleCaches() {
		log.info("일정/매치 상세 캐시(todaySchedules, dailySchedules, todayMatchDetails, matchDetails)를 무효화합니다.");
	}
}
