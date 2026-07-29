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
import org.springframework.transaction.support.TransactionTemplate;

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
	// 한 사이클에 429가 이 수 이상이면 systemic으로 보고 경고. 미만이면 간헐로 간주(로그만).
	private static final int RATE_LIMIT_ALERT_THRESHOLD = 10;

	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final ChampionDataService championDataService;
	private final RiotApiClient riotApiClient;
	private final RiotMonitorProperties riotMonitorProperties;
	private final NotificationService notificationService;
	private final PlayerSoloRankPushService playerSoloRankPushService;
	private final SchedulerAlertService schedulerAlertService;
	private final SoloRankGameHistoryRecorder soloRankGameHistoryRecorder;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 추적 계정의 현재 게임을 폴링한다.
	 *
	 * <p>예전엔 이 메서드 전체가 {@code @Transactional} 이었다. 그러면 계정 수만큼의 Riot API 호출,
	 * 디스코드 웹훅, FCM 발송이 모두 하나의 트랜잭션 안에서 일어나 커넥션과 행 락을 사이클 내내 붙잡는다.
	 * 429(Retry-After 20초 관측)가 겹치면 그 시간이 분 단위로 늘어난다. 실측 2026-07-29에
	 * {@code insert into member_favorite_player} 등이 락 대기 50초로 타임아웃된 건이 80건이었다.</p>
	 *
	 * <p>외부 호출은 트랜잭션 밖에서 하고, 계정 상태 변경만 {@link #persist}로 짧게 커밋한다.
	 * 계정 엔티티는 조회 시점에 detached 이므로 변경 후 반드시 persist 를 호출해야 반영된다
	 * (findAllTrackedAccounts 가 player 를 JOIN FETCH 하므로 연관 접근은 안전하다).</p>
	 */
	public PlayerSoloRankMonitorResult pollTrackedAccounts() {
		long startedAt = System.currentTimeMillis();
		riotApiClient.assertConfigured();
		List<PlayerRiotAccount> trackedAccounts = playerRiotAccountRepository.findAllTrackedAccounts();

		int checkedCount = 0;
		int noRecentMatchCount = 0;
		int unchangedCount = 0;
		int otherQueueCount = 0;
		int rankedSoloCount = 0;
		int alertsSentCount = 0;
			int failedCount = 0;
		int rateLimitedCount = 0;

		// 초당 호출 상한(버스트 429 방지). 호출 시작 간 최소 간격을 둔다.
		int maxPerSec = riotMonitorProperties.getMaxRequestsPerSecond();
		long minIntervalMs = maxPerSec > 0 ? 1000L / maxPerSec : 0;
		long lastCallAt = 0;

		for (PlayerRiotAccount account : trackedAccounts) {
			LocalDateTime checkedAt = LocalDateTime.now();
			try {
				lastCallAt = throttle(lastCallAt, minIntervalMs);
				Optional<RiotCurrentGameResponse> currentGameOptional = riotApiClient.getActiveGameByPuuid(
						account.getPuuid(), account.getPlatform());
				checkedCount++;

				if (currentGameOptional.isEmpty()) {
					account.markNoRecentMatch(checkedAt);
					persist(account);
					noRecentMatchCount++;
					continue;
				}

				RiotCurrentGameResponse currentGame = currentGameOptional.get();
				String currentGameId = String.valueOf(currentGame.gameId());
				String previousLastCheckedGameId = account.getLastCheckedMatchId();
				if (currentGameId.equals(previousLastCheckedGameId)) {
					account.markMatchCheckHeartbeat(checkedAt);
					persist(account);
					unchangedCount++;
					continue;
				}

				// 새 게임 1회만 챔피언 해석(추가 Riot 콜 없음 — spectator 응답에서 조회).
				Champion champion = resolveTrackedChampion(currentGame, account.getPuuid());

				if (isRankedSolo(currentGame)) {
					account.markRecentRankedSolo(currentGameId, checkedAt);
					persist(account);
					rankedSoloCount++;
					// 구독 여부와 무관하게 솔랭 게임 이력 적재(선수 카드 최근 솔랭·챔프 폭).
					soloRankGameHistoryRecorder.record(
							account.getPlayer(), currentGameId, champion, checkedAt);
				} else {
					account.markRecentOtherQueue(currentGameId, checkedAt);
					persist(account);
					otherQueueCount++;
				}

				if (previousLastCheckedGameId == null) {
					log.info("Primed live game baseline for player={} gameId={}",
							account.getPlayer().getName(),
							currentGameId);
					continue;
				}

				if (account.shouldSendAlertFor(currentGameId)) {
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
								buildOpggUrl(account.getGameName(), account.getTagLine(), account.getPlatform()));
					}
					account.markAlertSent(currentGameId);
					persist(account);
					alertsSentCount++;
				}
			} catch (RiotApiException e) {
				failedCount++;
				if (e.isRateLimited()) {
					// 429(server rate limit)는 Riot 공유·확률적 한도(Retry-After 20초 관측)라 낮은 rate에도 간헐 발생.
					// 해당 계정만 스킵 → 다음 60초 주기에 재시도(솔랭 게임 20~35분이라 감지엔 무해).
					// 개별 429는 알림하지 않고 로그만 — 예상된 일시 현상. 사이클 종합이 systemic일 때만 경고(아래).
					rateLimitedCount++;
					log.warn("Riot live poll rate limited (429) for player={}, skipping this cycle",
							account.getPlayer().getName());
				} else {
					log.warn("Riot live poll failed for player={}", account.getPlayer().getName(), e);
					schedulerAlertService.recordFailure(
							JOB_KEY,
							JOB_NAME,
							e,
							"player=" + account.getPlayer().getName());
				}
			}
		}

		// 429가 다수(systemic)일 때만 경고 — 간헐 1~2건은 정상(다음 주기 자동 복구)이라 노이즈로 알리지 않는다.
		if (rateLimitedCount >= RATE_LIMIT_ALERT_THRESHOLD) {
			schedulerAlertService.recordWarning(
					JOB_KEY,
					JOB_NAME,
					"Riot API rate limit: " + rateLimitedCount + "/" + trackedAccounts.size()
							+ " 계정 429 (systemic — 폴 주기·상한 점검 필요)");
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

	/**
	 * 계정 상태 변경만 짧은 트랜잭션으로 커밋한다. 외부 API 호출은 절대 이 안에 두지 않는다.
	 * 계정은 detached 라 save(merge)로 반영한다.
	 */
	private void persist(PlayerRiotAccount account) {
		transactionTemplate.executeWithoutResult(status -> playerRiotAccountRepository.save(account));
	}

	@Transactional(readOnly = true)
	public PlayerRiotAlertCheckResult checkAndSendAlertByPuuid(String puuid, String platform) {
		riotApiClient.assertConfigured();

		Optional<RiotCurrentGameResponse> currentGameOptional = riotApiClient.getActiveGameByPuuid(puuid, platform);
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

	// 호출 시작 간 최소 간격을 유지(초당 상한). 반환값(현재 시각)을 다음 호출의 lastCallAt으로 넘긴다.
	private long throttle(long lastCallAt, long minIntervalMs) {
		if (minIntervalMs <= 0 || lastCallAt == 0) {
			return System.currentTimeMillis();
		}
		long wait = minIntervalMs - (System.currentTimeMillis() - lastCallAt);
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return System.currentTimeMillis();
	}

	/** OP.GG 소환사 페이지 URL (디스코드 알림과 동일 포맷). 계정 플랫폼별 지역 코드 사용. 정보 부족 시 빈 문자열. */
	private String buildOpggUrl(String gameName, String tagLine, String platform) {
		if (gameName == null || gameName.isBlank() || tagLine == null || tagLine.isBlank()) {
			return "";
		}
		String path = URLEncoder.encode(gameName + "-" + tagLine, StandardCharsets.UTF_8);
		return "https://www.op.gg/summoners/" + RiotPlatform.opggRegion(platform) + "/" + path;
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
