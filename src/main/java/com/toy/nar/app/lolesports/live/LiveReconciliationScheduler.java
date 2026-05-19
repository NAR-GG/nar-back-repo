package com.toy.nar.app.lolesports.live;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lolesports.live", name = "enabled", havingValue = "true")
public class LiveReconciliationScheduler {

	private final LiveReconciliationService reconciliationService;

	@Scheduled(fixedDelayString = "${lolesports.live.reconcile-interval-ms:60000}")
	public void reconcile() {
		try {
			reconciliationService.reconcileRecentGames();
		} catch (Exception e) {
			log.warn("Live reconciliation failed: {}", e.getMessage());
		}
	}
}
