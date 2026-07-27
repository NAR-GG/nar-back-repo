package com.toy.nar.app.riot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerSoloRankMatchFallbackScheduler {

	private final PlayerSoloRankMatchFallbackService playerSoloRankMatchFallbackService;
	private final RiotMatchFallbackProperties riotMatchFallbackProperties;

	@Scheduled(
			fixedDelayString = "${riot.match-fallback.poll-interval-ms:300000}",
			initialDelayString = "${riot.match-fallback.initial-delay-ms:45000}")
	public void pollCompletedSoloRankMatches() {
		if (!riotMatchFallbackProperties.isEnabled()) {
			return;
		}
		playerSoloRankMatchFallbackService.pollTrackedAccounts();
		log.debug("Completed scheduled solo rank match fallback poll");
	}
}
