package com.toy.nar.app.riot;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.toy.nar.app.data.source.ChampionDataService;
import com.toy.nar.app.mobile.push.PlayerSoloRankPushService;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 선수가 솔랭 한 판을 마쳤을 때 구독자에게 결과를 보낸다.
 *
 * <p>감지는 라이브 모니터의 상태 전이를 쓴다 — {@code IN_RANKED_SOLO → OFFLINE} 이 곧 "그 게임이
 * 끝났다"이고, {@code lastCheckedMatchId} 가 어느 게임인지 알려준다. 전 계정을 1분마다 match-v5 로
 * 훑는 방식과 비교하면 지연은 같고(둘 다 폴 주기가 상한) 호출은 훨씬 적다 — 폴링은 아무 일도
 * 없는 계정에까지 매분 묻지만(101계정 기준 시간당 6,060회) 이쪽은 실제로 끝난 게임에만 낸다
 * (하루 257게임, 피크 시간당 40회).</p>
 *
 * <p>게임이 끝나도 match-v5 는 곧바로 발행되지 않는다. 조회에 실패하면 다음 스윕에서 다시 시도하고,
 * {@link #MAX_ATTEMPTS} 를 넘기면 포기한다. 이때 결과를 못 실으므로 알림을 보내지 않는다 —
 * 승패·KDA 없는 "한 판 마쳤어요"는 알림 값어치가 없다.</p>
 *
 * <p>스트리머 모드 계정은 애초에 라이브 감지가 안 돼 전이도 없다. 그쪽은
 * {@link PlayerSoloRankMatchFallbackService} 가 커버한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloRankEndNotificationService {

	/** 이 횟수만큼 재시도해도 match-v5 가 없으면 포기한다. 스윕 주기 30초 × 10 = 5분. */
	private static final int MAX_ATTEMPTS = 10;

	private static final int RANKED_SOLO_QUEUE_ID = 420;

	private final RiotApiClient riotApiClient;
	private final ChampionDataService championDataService;
	private final PlayerSoloRankPushService playerSoloRankPushService;
	private final PlayerSoloRankGameRepository soloRankGameRepository;
	private final SoloRankEndNotificationProperties properties;

	/**
	 * 결과를 기다리는 게임들. 재기동하면 비는데, 그때는 그 게임의 종료 알림만 못 나간다 —
	 * 테이블을 만들 값어치가 없다.
	 *
	 * <p>ponytail: 인메모리. 다중 인스턴스로 가면 인스턴스마다 자기가 감지한 것만 처리하므로
	 * 중복 발송은 없다(멱등 키가 DB 에 있다).</p>
	 */
	private final Map<String, Pending> pending = new ConcurrentHashMap<>();

	/** 라이브 모니터가 "이 계정의 이 게임이 방금 끝났다"를 알릴 때 호출한다. */
	public void onGameEnded(PlayerRiotAccount account, String gameId) {
		if (!properties.isEnabled() || account == null || gameId == null || gameId.isBlank()) {
			return;
		}
		String key = account.getPlayer().getId() + "#" + gameId;
		pending.putIfAbsent(key, new Pending(account, gameId));
		log.info("[solo-rank-end] 종료 감지 player={} gameId={}", account.getPlayer().getName(), gameId);
	}

	/**
	 * 대기 중인 게임들의 결과를 확인해 발송한다. 스케줄러가 주기적으로 부른다.
	 *
	 * @return 이번 스윕에서 발송한 건수
	 */
	public int sweep() {
		if (!properties.isEnabled() || pending.isEmpty()) {
			return 0;
		}
		int sent = 0;
		for (Map.Entry<String, Pending> entry : Map.copyOf(pending).entrySet()) {
			Pending target = entry.getValue();
			if (target.attempts.incrementAndGet() > MAX_ATTEMPTS) {
				pending.remove(entry.getKey());
				log.info("[solo-rank-end] match-v5 미발행으로 포기 player={} gameId={}",
						target.account.getPlayer().getName(), target.gameId);
				continue;
			}
			try {
				Outcome outcome = notifyIfFinished(target);
				if (outcome != Outcome.RETRY) {
					pending.remove(entry.getKey());
				}
				if (outcome == Outcome.SENT) {
					sent++;
				}
			} catch (RiotApiException e) {
				// 429·타임아웃은 다음 스윕에서 다시 시도한다.
				log.debug("[solo-rank-end] 결과 조회 실패(재시도) player={} gameId={}: {}",
						target.account.getPlayer().getName(), target.gameId, e.getMessage());
			} catch (Exception e) {
				pending.remove(entry.getKey());
				log.warn("[solo-rank-end] 종료 알림 실패 player={} gameId={}",
						target.account.getPlayer().getName(), target.gameId, e);
			}
		}
		return sent;
	}

	/** 스윕 한 건의 결과. RETRY 만 대기열에 남고, 발송 건수는 SENT 만 센다. */
	private enum Outcome {
		SENT,
		/** 보낼 일이 없어 끝난 경우(이미 보냄·솔랭 아님). */
		DONE,
		/** match-v5 가 아직 없어 다음 스윕에서 다시 봐야 하는 경우. */
		RETRY
	}

	private Outcome notifyIfFinished(Pending target) {
		PlayerRiotAccount account = target.account;
		Player player = account.getPlayer();

		// 이미 종료 알림을 낸 게임이면 다시 조회하지 않는다. 재기동 직후 같은 게임이
		// 다시 큐에 들어와도 Riot 호출을 한 번 더 태우지 않는다.
		if (soloRankGameRepository.existsByPlayer_IdAndGameIdAndEndNotifiedAtIsNotNull(
				player.getId(), target.gameId)) {
			return Outcome.DONE;
		}

		RiotMatchResponse match = riotApiClient.getMatch(
				account.getPlatform() == null ? null : matchIdOf(account, target.gameId),
				account.getPlatform());
		if (match == null || match.info() == null) {
			return Outcome.RETRY;
		}
		if (match.info().queueId() == null || match.info().queueId() != RANKED_SOLO_QUEUE_ID) {
			return Outcome.DONE;
		}
		if (match.info().gameEndTimestamp() == null) {
			// 아직 진행 중으로 내려온다 — 다음 스윕에서 다시 본다.
			return Outcome.RETRY;
		}

		RiotMatchResponse.Participant tracked =
				SoloRankMatchResultFormatter.findParticipant(match, account.getPuuid());
		Champion champion = tracked == null || tracked.championId() == null
				? null
				: championDataService.findChampionByRiotKey(tracked.championId()).orElse(null);

		playerSoloRankPushService.notifySubscribersPostGame(
				player,
				target.gameId,
				champion == null ? null : champion.getChampionNameKr(),
				champion == null ? null : champion.getImageUrl(),
				SoloRankMatchResultFormatter.resultLine(
						champion == null ? null : champion.getChampionNameKr(), tracked),
				tracked == null ? null : tracked.win(),
				SoloRankMatchResultFormatter.kda(tracked),
				RiotPlatform.opggUrl(account.getGameName(), account.getTagLine(), account.getPlatform()));

		soloRankGameRepository.markEndNotified(player.getId(), target.gameId, LocalDateTime.now());
		log.info("[solo-rank-end] 종료 알림 발송 player={} gameId={}", player.getName(), target.gameId);
		return Outcome.SENT;
	}

	/** spectator 게임 ID 는 플랫폼 접두사가 없다. match-v5 조회는 접두사가 필요하다. */
	private String matchIdOf(PlayerRiotAccount account, String gameId) {
		return account.getPlatform().toUpperCase() + "_" + gameId;
	}

	private static final class Pending {
		private final PlayerRiotAccount account;
		private final String gameId;
		private final AtomicInteger attempts = new AtomicInteger();

		private Pending(PlayerRiotAccount account, String gameId) {
			this.account = account;
			this.gameId = gameId;
		}
	}
}
