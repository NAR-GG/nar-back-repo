package com.toy.nar.app.riot;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 솔랭 종료 결과 대기열 스윕. 게임이 끝나도 match-v5 는 바로 발행되지 않아 재시도가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoloRankEndNotificationScheduler {

	private final SoloRankEndNotificationService endNotificationService;
	private final SoloRankEndNotificationProperties properties;

	@Scheduled(
			fixedDelayString = "${solo-rank.end-notification.sweep-interval-ms:30000}",
			initialDelayString = "${solo-rank.end-notification.initial-delay-ms:60000}")
	public void sweepPendingResults() {
		if (!properties.isEnabled()) {
			return;
		}
		int sent = endNotificationService.sweep();
		if (sent > 0) {
			log.info("[solo-rank-end] 스윕 발송 {}건", sent);
		}
	}
}
