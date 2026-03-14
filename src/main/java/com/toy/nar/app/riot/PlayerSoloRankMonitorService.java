package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerSoloRankMonitorResult;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloRankMonitorService {

	private static final int RANKED_SOLO_QUEUE_ID = 420;
	private static final String JOB_KEY = "PLAYER_RANKED_SOLO_MONITOR";
	private static final String JOB_NAME = "선수 솔랭 감시";

	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final RiotApiClient riotApiClient;
	private final RiotMonitorProperties riotMonitorProperties;
	private final NotificationService notificationService;
	private final SchedulerAlertService schedulerAlertService;

	@Transactional
	public PlayerSoloRankMonitorResult pollTrackedAccounts() {
		long startedAt = System.currentTimeMillis();
		List<PlayerRiotAccount> trackedAccounts = playerRiotAccountRepository.findTrackedAccountsByPlatform(
				riotMonitorProperties.getPlatform().toUpperCase());

		int checkedCount = 0;
		int noRecentMatchCount = 0;
		int unchangedCount = 0;
		int otherQueueCount = 0;
		int rankedSoloCount = 0;
		int alertsSentCount = 0;
		int failedCount = 0;

		for (PlayerRiotAccount account : trackedAccounts) {
			LocalDateTime checkedAt = LocalDateTime.now();
			try {
				List<String> recentMatchIds = riotApiClient.getRecentMatchIdsByPuuid(
						account.getPuuid(),
						riotMonitorProperties.getRecentMatchFetchCount());
				checkedCount++;

				if (recentMatchIds.isEmpty()) {
					account.markNoRecentMatch(checkedAt);
					noRecentMatchCount++;
					continue;
				}

				String latestMatchId = recentMatchIds.get(0);
				String previousLastCheckedMatchId = account.getLastCheckedMatchId();
				if (latestMatchId.equals(previousLastCheckedMatchId)) {
					account.markMatchCheckHeartbeat(checkedAt);
					unchangedCount++;
					continue;
				}

				List<String> unseenMatchIds = collectUnseenMatchIds(recentMatchIds, previousLastCheckedMatchId);
				RiotMatchResponse latestMatch = riotApiClient.getMatch(latestMatchId);

				if (isRankedSolo(latestMatch)) {
					account.markRecentRankedSolo(latestMatchId, checkedAt);
					rankedSoloCount++;
				} else {
					account.markRecentOtherQueue(latestMatchId, checkedAt);
					otherQueueCount++;
				}

				if (previousLastCheckedMatchId == null) {
					log.info("Primed latest match baseline for player={} matchId={}",
							account.getPlayer().getName(),
							latestMatchId);
					continue;
				}

				alertsSentCount += sendAlertsForUnseenRankedSoloMatches(account, unseenMatchIds, latestMatchId, latestMatch);
			} catch (RiotApiException e) {
				failedCount++;
				log.warn("Riot live poll failed for player={}", account.getPlayer().getName(), e);
				if (e.isRateLimited()) {
					schedulerAlertService.recordWarning(
							JOB_KEY,
							JOB_NAME,
							"Riot API rate limit reached while polling KR ranked solo monitor");
				} else {
					schedulerAlertService.recordFailure(
							JOB_KEY,
							JOB_NAME,
							e,
							"player=" + account.getPlayer().getName());
				}
			}
		}

		long elapsed = System.currentTimeMillis() - startedAt;
		schedulerAlertService.recordSuccess(JOB_KEY, JOB_NAME, elapsed);
		return new PlayerSoloRankMonitorResult(
				trackedAccounts.size(),
				checkedCount,
				noRecentMatchCount,
				unchangedCount,
				otherQueueCount,
				rankedSoloCount,
				alertsSentCount,
				failedCount);
	}

	private List<String> collectUnseenMatchIds(List<String> recentMatchIds, String lastCheckedMatchId) {
		List<String> unseenMatchIds = new ArrayList<>();
		for (String matchId : recentMatchIds) {
			if (matchId.equals(lastCheckedMatchId)) {
				break;
			}
			unseenMatchIds.add(matchId);
		}
		return unseenMatchIds;
	}

	private int sendAlertsForUnseenRankedSoloMatches(
			PlayerRiotAccount account,
			List<String> unseenMatchIds,
			String latestMatchId,
			RiotMatchResponse latestMatch) {
		int alertsSentCount = 0;
		for (int i = unseenMatchIds.size() - 1; i >= 0; i--) {
			String unseenMatchId = unseenMatchIds.get(i);
			RiotMatchResponse match = unseenMatchId.equals(latestMatchId)
					? latestMatch
					: riotApiClient.getMatch(unseenMatchId);
			if (!isRankedSolo(match) || !account.shouldSendAlertFor(unseenMatchId)) {
				continue;
			}

			notificationService.sendPlayerRankedSoloNotification(
					account.getPlayer().getName(),
					account.getRiotId(),
					account.getGameName(),
					account.getTagLine(),
					unseenMatchId);
			account.markAlertSent(unseenMatchId);
			alertsSentCount++;
		}
		return alertsSentCount;
	}

	private boolean isRankedSolo(RiotMatchResponse match) {
		return match.info() != null && match.info().queueId() != null && match.info().queueId() == RANKED_SOLO_QUEUE_ID;
	}
}
