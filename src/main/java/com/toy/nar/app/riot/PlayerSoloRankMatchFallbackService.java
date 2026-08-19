package com.toy.nar.app.riot;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.monitor.SchedulerAlertService;
import com.toy.nar.app.riot.dto.PlayerSoloRankMatchFallbackResult;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.common.util.KoreanParticle;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 솔랭 완료 매치(match-v5) 폴백 감지.
 *
 * <p>스트리머 모드 계정은 spectator-v5가 라이브 게임을 내려주지 않아
 * ({@code "filtered"} 404) 라이브 모니터가 영구히 놓친다. match-v5 완료 매치
 * 목록은 필터링되지 않으므로, 경기 종료 후 새 솔랭 게임을 감지해 이력 적재·알림한다.
 *
 * <p>중복 방지: 게임 ID를 라이브 모니터와 같은 형식(플랫폼 접두사 제거)으로 정규화하고,
 * {@link SoloRankGameHistoryRecorder}의 신규 적재 여부를 알림 게이트로 쓴다.
 * 라이브 모니터가 이미 잡은 게임은 이력에 존재 → 알림 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSoloRankMatchFallbackService {

	private static final String JOB_KEY = "riot-solo-rank-match-fallback";
	private static final String JOB_NAME = "솔랭 완료 매치 폴백 감지";
	private static final int RANKED_SOLO_QUEUE_ID = 420;

	private final PlayerRiotAccountRepository playerRiotAccountRepository;
	private final RiotApiClient riotApiClient;
	private final RiotMatchFallbackProperties properties;
	private final ChampionDataService championDataService;
	private final NotificationService notificationService;
	private final PlayerSoloRankPushService playerSoloRankPushService;
	private final SoloRankGameHistoryRecorder soloRankGameHistoryRecorder;
	private final SchedulerAlertService schedulerAlertService;

	@Transactional(readOnly = true)
	public PlayerSoloRankMatchFallbackResult pollTrackedAccounts() {
		long startedAt = System.currentTimeMillis();
		riotApiClient.assertConfigured();
		List<PlayerRiotAccount> trackedAccounts = playerRiotAccountRepository.findAllTrackedAccounts();

		int checkedCount = 0;
		int newGameCount = 0;
		int alertsSentCount = 0;
		int failedCount = 0;

		for (PlayerRiotAccount account : trackedAccounts) {
			try {
				List<String> matchIds = riotApiClient.getRecentSoloRankMatchIdsByPuuid(
						account.getPuuid(), properties.getFetchCount(), account.getPlatform());
				checkedCount++;

				for (String matchId : matchIds) {
					String gameId = SoloRankMatchResultFormatter.normalizeGameId(matchId);
					if (soloRankGameHistoryRecorder.exists(account.getPlayer().getId(), gameId)) {
						continue;
					}
					RiotMatchResponse match = riotApiClient.getMatch(matchId, account.getPlatform());
					if (match == null || match.info() == null
							|| match.info().queueId() == null
							|| match.info().queueId() != RANKED_SOLO_QUEUE_ID) {
						continue;
					}

					RiotMatchResponse.Participant tracked =
							SoloRankMatchResultFormatter.findParticipant(match, account.getPuuid());
					Champion champion = tracked == null || tracked.championId() == null
							? null
							: championDataService.findChampionByRiotKey(tracked.championId()).orElse(null);

					boolean newlyRecorded = soloRankGameHistoryRecorder.record(
							account.getPlayer(), gameId, champion, LocalDateTime.now());
					if (!newlyRecorded) {
						continue;
					}
					newGameCount++;

					if (isFresh(match.info().gameEndTimestamp())) {
						sendAlerts(account, gameId, champion, tracked, match.info().durationSeconds());
						alertsSentCount++;
					}
				}
			} catch (RiotApiException e) {
				failedCount++;
				log.warn("Solo rank match fallback poll failed for player={}",
						account.getPlayer().getName(), e);
				if (e.isRateLimited()) {
					schedulerAlertService.recordWarning(
							JOB_KEY, JOB_NAME,
							"Riot API rate limit reached while polling match fallback");
				} else {
					schedulerAlertService.recordFailure(
							JOB_KEY, JOB_NAME, e,
							"player=" + account.getPlayer().getName());
				}
			}
		}

		long elapsed = System.currentTimeMillis() - startedAt;
		schedulerAlertService.recordSuccess(JOB_KEY, JOB_NAME, elapsed);
		return new PlayerSoloRankMatchFallbackResult(
				trackedAccounts.size(), checkedCount, newGameCount, alertsSentCount, failedCount);
	}

	private void sendAlerts(
			PlayerRiotAccount account,
			String gameId,
			Champion champion,
			RiotMatchResponse.Participant tracked,
			Integer gameDurationSeconds) {
		String championName = champion == null ? null : champion.getChampionNameKr();
		String championIconUrl = champion == null ? null : champion.getImageUrl();
		String resultLine = SoloRankMatchResultFormatter.resultLine(championName, tracked);

		notificationService.sendPlayerGameNotification(
				account.getPlayer().getName(),
				account.getRiotId(),
				account.getGameName(),
				account.getTagLine(),
				gameId,
				"솔로 랭크 (경기 종료·폴백)",
				championName,
				championIconUrl);
		playerSoloRankPushService.notifySubscribersPostGame(
				account.getPlayer(),
				gameId,
				championName,
				championIconUrl,
				resultLine,
				tracked == null ? null : tracked.win(),
				SoloRankMatchResultFormatter.kda(tracked),
				gameDurationSeconds,
				RiotPlatform.opggUrl(account.getGameName(), account.getTagLine(), account.getPlatform()));
	}



	private boolean isFresh(Long gameEndTimestampMs) {
		if (gameEndTimestampMs == null) {
			return false;
		}
		Duration age = Duration.between(Instant.ofEpochMilli(gameEndTimestampMs), Instant.now());
		return !age.isNegative() && age.toMinutes() <= properties.getAlertFreshnessMinutes();
	}


}
