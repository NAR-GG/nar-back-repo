package com.toy.nar.app.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheEvictionService {

	/**
	 * 'todaySchedules' 캐시에 있는 모든 데이터를 삭제합니다.
	 * 스케줄러가 데이터 동기화를 완료한 후 이 메서드를 호출합니다.
	 */
	@CacheEvict(value = "todaySchedules", allEntries = true)
	public void evictTodayScheduleCache() {
		log.info("'todaySchedules' 캐시의 모든 데이터를 무효화합니다.");
	}
}