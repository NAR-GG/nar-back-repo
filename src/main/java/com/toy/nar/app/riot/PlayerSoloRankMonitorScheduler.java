package com.toy.nar.app.riot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerSoloRankMonitorScheduler {

	private final PlayerSoloRankMonitorService playerSoloRankMonitorService;
	private final RiotMonitorProperties riotMonitorProperties;

	@Scheduled(
			fixedDelayString = "${riot.monitor.poll-interval-ms:60000}",
			initialDelayString = "${riot.monitor.initial-delay-ms:15000}")
	public void pollRankedSoloPlayers() {
		if (!riotMonitorProperties.isEnabled()) {
			return;
		}
		playerSoloRankMonitorService.pollTrackedAccounts();
		log.debug("Completed scheduled recent ranked solo monitor poll");
	}
}
