package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloRankMonitorService {

	private static final int RANKED_SOLO_QUEUE_ID = 420;
	private static final int RANKED_FLEX_QUEUE_ID = 440;
	private static final int ARAM_QUEUE_ID = 450;
	private static final int NORMAL_DRAFT_QUEUE_ID = 400;
	private static final int NORMAL_BLIND_QUEUE_ID = 430;
	private static final int QUICKPLAY_QUEUE_ID = 490;
	private static final int ARENA_QUEUE_ID = 1700;
	private static final int ARENA_RANKED_QUEUE_ID = 1710;
	private static final String JOB_KEY = "PLAYER_RANKED_SOLO_MONITOR";
	private static final String JOB_NAME = "선수 솔랭 감시";

	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final ChampionDataService championDataService;
	private final RiotApiClient riotApiClient;
	private final RiotMonitorProperties riotMonitorProperties;
	private final NotificationService notificationService;
	private final PlayerSoloRankPushService playerSoloRankPushService;
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

				if (account.shouldSendAlertFor(currentGameId)) {
					Champion champion = resolveTrackedChampion(currentGame, account.getPuuid());
					String queueDisplayName = resolveQueueDisplayName(currentGame.gameQueueConfigId());
					notificationService.sendPlayerGameNotification(
							account.getPlayer().getName(),
							account.getRiotId(),
							account.getGameName(),
							account.getTagLine(),
							currentGameId,
							queueDisplayName,
							champion == null ? null : champion.getChampionNameKr(),
							champion == null ? null : champion.getImageUrl());
					if (isRankedSolo(currentGame)) {
						playerSoloRankPushService.notifySubscribers(
								account.getPlayer(),
								currentGameId,
								champion == null ? null : champion.getChampionNameKr(),
								champion == null ? null : champion.getImageUrl(),
								queueDisplayName,
								buildOpggUrl(account.getGameName(), account.getTagLine()));
					}
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
					"OFFLINE",
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
		String queueName = resolveQueueName(currentGame.gameQueueConfigId());
		String queueDisplayName = resolveQueueDisplayName(currentGame.gameQueueConfigId());

		RiotIdentity riotIdentity = parseRiotIdentity(riotId, puuid);
		notificationService.sendPlayerGameNotification(
				riotIdentity.displayName(),
				riotId,
				riotIdentity.gameName(),
				riotIdentity.tagLine(),
				gameId,
				queueDisplayName,
				championName,
				championIconUrl);

		return new PlayerRiotAlertCheckResult(
				puuid,
				true,
				isRankedSolo(currentGame),
				true,
				gameId,
				currentGame.gameQueueConfigId(),
				queueName,
				riotId,
				championName,
				championIconUrl,
				"ALERT_SENT");
	}

	/** OP.GG 소환사 페이지 URL (디스코드 알림과 동일 포맷). 정보 부족 시 빈 문자열. */
	private String buildOpggUrl(String gameName, String tagLine) {
		if (gameName == null || gameName.isBlank() || tagLine == null || tagLine.isBlank()) {
			return "";
		}
		String path = URLEncoder.encode(gameName + "-" + tagLine, StandardCharsets.UTF_8);
		return "https://www.op.gg/summoners/kr/" + path;
	}

	private boolean isRankedSolo(RiotCurrentGameResponse currentGame) {
		return currentGame.gameQueueConfigId() != null && currentGame.gameQueueConfigId() == RANKED_SOLO_QUEUE_ID;
	}

	private String resolveQueueName(Integer queueId) {
		if (queueId == null) {
			return "UNKNOWN_QUEUE";
		}
		return switch (queueId) {
			case RANKED_SOLO_QUEUE_ID -> "RANKED_SOLO";
			case RANKED_FLEX_QUEUE_ID -> "RANKED_FLEX";
			case ARAM_QUEUE_ID -> "ARAM";
			case NORMAL_DRAFT_QUEUE_ID -> "NORMAL_DRAFT";
			case NORMAL_BLIND_QUEUE_ID -> "NORMAL_BLIND";
			case QUICKPLAY_QUEUE_ID -> "QUICKPLAY";
			case ARENA_QUEUE_ID, ARENA_RANKED_QUEUE_ID -> "ARENA";
			default -> "OTHER_GAME";
		};
	}

	private String resolveQueueDisplayName(Integer queueId) {
		if (queueId == null) {
			return "기타 게임";
		}
		return switch (queueId) {
			case RANKED_SOLO_QUEUE_ID -> "솔로 랭크";
			case RANKED_FLEX_QUEUE_ID -> "자유 랭크";
			case ARAM_QUEUE_ID -> "칼바람 나락";
			case NORMAL_DRAFT_QUEUE_ID -> "일반 Draft";
			case NORMAL_BLIND_QUEUE_ID -> "일반 Blind";
			case QUICKPLAY_QUEUE_ID -> "빠른 대전";
			case ARENA_QUEUE_ID, ARENA_RANKED_QUEUE_ID -> "아레나";
			default -> "기타 게임";
		};
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
