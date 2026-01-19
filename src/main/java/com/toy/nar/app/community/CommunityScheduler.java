package com.toy.nar.app.community;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityScheduler {

	private final CommunityService communityService;

	// 1. 데이터 동기화: 10분마다 실행 (최신글 & 인기글)
	// cron = "초 분 시 일 월 요일"
	// @Scheduled(cron = "0 0/10 * * * *")
	public void syncCommunityData() {
		log.info("Starting scheduled community and news sync...");
		try {
			// 최신글 & 뉴스 동기화
			communityService.syncAll("latest");
			// 인기글 & 뉴스 동기화
			communityService.syncAll("popular");
		} catch (Exception e) {
			log.error("Scheduled community sync failed", e);
		}
		log.info("Scheduled community sync completed.");
	}

	// 2. 오래된 데이터 삭제: 매일 새벽 4시 실행
	// @Scheduled(cron = "0 0 4 * * *")
	public void cleanupOldData() {
		log.info("Starting scheduled community cleanup...");
		try {
			// 7일 지난 데이터 삭제
			communityService.deletePostsOlderThan(7);
		} catch (Exception e) {
			log.error("Scheduled cleanup failed", e);
		}
		log.info("Scheduled community cleanup completed.");
	}
}
