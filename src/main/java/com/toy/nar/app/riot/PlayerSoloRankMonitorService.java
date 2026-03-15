package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerRiotAlertCheckResult;
import com.toy.nar.app.riot.dto.PlayerSoloRankMonitorResult;
import com.toy.nar.app.riot.dto.RiotCurrentGameResponse;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloRankMonitorService {

	private static final int RANKED_SOLO_QUEUE_ID = 420;
	private static final String JOB_KEY = "PLAYER_RANKED_SOLO_MONITOR";
	private static final String JOB_NAME = "선수 솔랭 감시";

	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final ChampionDataService championDataService;
	private final RiotApiClient riotApiClient;
	private final RiotMonitorProperties riotMonitorProperties;
	private final NotificationService notificationService;
	private final SchedulerAlertService schedulerAlertService;

	@Transactional
	public PlayerSoloRankMonitorResult pollTrackedAccounts() {
		long startedAt = System.currentTimeMillis();
		riotApiClient.assertConfigured();
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
				Optional<RiotCurrentGameResponse> currentGameOptional = riotApiClient.getActiveGameByPuuid(account.getPuuid());
				checkedCount++;

				if (currentGameOptional.isEmpty()) {
					account.markNoRecentMatch(checkedAt);
					noRecentMatchCount++;
					continue;
				}

				RiotCurrentGameResponse currentGame = currentGameOptional.get();
				String currentGameId = String.valueOf(currentGame.gameId());
				String previousLastCheckedGameId = account.getLastCheckedMatchId();
				if (currentGameId.equals(previousLastCheckedGameId)) {
					account.markMatchCheckHeartbeat(checkedAt);
					unchangedCount++;
					continue;
				}

				if (isRankedSolo(currentGame)) {
					account.markRecentRankedSolo(currentGameId, checkedAt);
					rankedSoloCount++;
				} else {
					account.markRecentOtherQueue(currentGameId, checkedAt);
					otherQueueCount++;
				}

				if (previousLastCheckedGameId == null) {
					log.info("Primed live game baseline for player={} gameId={}",
							account.getPlayer().getName(),
							currentGameId);
					continue;
				}

				if (isRankedSolo(currentGame) && account.shouldSendAlertFor(currentGameId)) {
					Champion champion = resolveTrackedChampion(currentGame, account.getPuuid());
					notificationService.sendPlayerRankedSoloNotification(
							account.getPlayer().getName(),
							account.getRiotId(),
							account.getGameName(),
							account.getTagLine(),
							currentGameId,
							champion == null ? null : champion.getChampionNameKr(),
							champion == null ? null : champion.getImageUrl());
					account.markAlertSent(currentGameId);
					alertsSentCount++;
				}
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

	@Transactional(readOnly = true)
	public PlayerRiotAlertCheckResult checkAndSendAlertByPuuid(String puuid) {
		riotApiClient.assertConfigured();

		Optional<RiotCurrentGameResponse> currentGameOptional = riotApiClient.getActiveGameByPuuid(puuid);
		if (currentGameOptional.isEmpty()) {
			return new PlayerRiotAlertCheckResult(
					puuid,
					false,
					false,
					false,
					null,
					null,
					null,
					null,
					null,
					"ACTIVE_GAME_NOT_FOUND");
		}

		RiotCurrentGameResponse currentGame = currentGameOptional.get();
		RiotCurrentGameResponse.RiotCurrentGameParticipantResponse participant = findTrackedParticipant(currentGame, puuid)
				.orElse(null);
		Champion champion = resolveTrackedChampion(currentGame, puuid);
		String riotId = participant == null || participant.riotId() == null || participant.riotId().isBlank()
				? puuid
				: participant.riotId();
		String championName = champion == null ? null : champion.getChampionNameKr();
		String championIconUrl = champion == null ? null : champion.getImageUrl();
		String gameId = currentGame.gameId() == null ? null : String.valueOf(currentGame.gameId());

		if (!isRankedSolo(currentGame)) {
			return new PlayerRiotAlertCheckResult(
					puuid,
					true,
					false,
					false,
					gameId,
					currentGame.gameQueueConfigId(),
					riotId,
					championName,
					championIconUrl,
					"CURRENT_GAME_IS_NOT_RANKED_SOLO");
		}

		RiotIdentity riotIdentity = parseRiotIdentity(riotId, puuid);
		notificationService.sendPlayerRankedSoloNotification(
				riotIdentity.displayName(),
				riotId,
				riotIdentity.gameName(),
				riotIdentity.tagLine(),
				gameId,
				championName,
				championIconUrl);

		return new PlayerRiotAlertCheckResult(
				puuid,
				true,
				true,
				true,
				gameId,
				currentGame.gameQueueConfigId(),
				riotId,
				championName,
				championIconUrl,
				"ALERT_SENT");
	}

	private boolean isRankedSolo(RiotCurrentGameResponse currentGame) {
		return currentGame.gameQueueConfigId() != null && currentGame.gameQueueConfigId() == RANKED_SOLO_QUEUE_ID;
	}

	private Champion resolveTrackedChampion(RiotCurrentGameResponse currentGame, String trackedPuuid) {
		return findTrackedParticipant(currentGame, trackedPuuid)
				.map(RiotCurrentGameResponse.RiotCurrentGameParticipantResponse::championId)
				.filter(championId -> championId != null)
				.flatMap(championDataService::findChampionByRiotKey)
				.orElse(null);
	}

	private Optional<RiotCurrentGameResponse.RiotCurrentGameParticipantResponse> findTrackedParticipant(
			RiotCurrentGameResponse currentGame,
			String trackedPuuid) {
		if (trackedPuuid == null || trackedPuuid.isBlank() || currentGame.participants() == null) {
			return Optional.empty();
		}
		return currentGame.participants().stream()
				.filter(participant -> trackedPuuid.equals(participant.puuid()))
				.findFirst();
	}

	private RiotIdentity parseRiotIdentity(String riotId, String fallbackName) {
		if (riotId == null || riotId.isBlank()) {
			return new RiotIdentity(fallbackName, null, fallbackName);
		}
		int delimiterIndex = riotId.indexOf('#');
		if (delimiterIndex < 0) {
			return new RiotIdentity(riotId, null, riotId);
		}
		String gameName = riotId.substring(0, delimiterIndex);
		String tagLine = riotId.substring(delimiterIndex + 1);
		return new RiotIdentity(gameName, tagLine, gameName);
	}

	private record RiotIdentity(String gameName, String tagLine, String displayName) {
	}
}
