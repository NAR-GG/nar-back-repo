package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.schedule.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lolesports.live", name = "enabled", havingValue = "true")
public class LivePollingScheduler {

	private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final long INITIAL_LOOKBACK_SECONDS = 90L;
	/** 라이브 엣지 추적: 이보다 더 뒤처지면 엣지 근처로 점프해 catch-up 지연(분 단위)을 막는다. */
	private static final long MAX_LAG_SECONDS = 50L;
	/** 피드는 window end-time이 20초보다 최신이면 거부하므로, 이보다 최신은 요청하지 않는다. */
	private static final long MIN_FEED_AGE_SECONDS = 35L;

	private final WorldsService worldsService;
	private final LiveStatsClient liveStatsClient;
	private final LiveObjectEventRecorder liveObjectEventRecorder;
	private final LiveStateStore liveStateStore;
	private final LiveFrameProcessor liveFrameProcessor;
	private final LiveGameMetadataService liveGameMetadataService;
	private final LeagueMatchService leagueMatchService;
	private final CacheEvictionService cacheEvictionService;
	private final NotificationService notificationService;
	private final com.toy.nar.app.mobile.push.TeamLiveEventPushService teamLiveEventPushService;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.stale-threshold-ms:180000}")
	private long staleThresholdMs;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.max-consecutive-failures:6}")
	private int maxConsecutiveFailures;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.notification.enabled:false}")
	private boolean liveNotificationEnabled;

	// 디스코드 알림을 보낼 리그(쉼표 구분). 기본 LCK 만. 그 외 리그는 데이터 수집만 하고 알림은 보내지 않는다.
	@org.springframework.beans.factory.annotation.Value("${lolesports.live.notification.leagues:LCK}")
	private String notificationLeagues;

	@Scheduled(fixedDelayString = "${lolesports.live.discovery-interval-ms:60000}")
	public void discoverLiveGames() {
		Map<String, ActiveLiveGame> activeGames = liveStateStore.getActiveGames();
		Set<String> discoveredGameIds = new HashSet<>();
		Set<String> activeMatchIds = activeMatchIds(activeGames);
		LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
		boolean scheduleCacheDirty = false;

		// 디스커버리 대상 리그를 좁힌다(9개 전부 매 틱 외부 호출하던 것을 줄임):
		//  (1) 현재 active 게임의 리그 — 진행 중인 라이브가 끊기지 않도록 무조건 포함
		//  (2) 오늘 ±1일에 경기가 있는 리그 — 곧 시작/방금 끝난 경기 커버
		// 둘 다 없으면(경기 없는 시간대) 외부 호출 0회. 무효 리그명은 WorldsService 가 알아서 skip.
		Set<String> leaguesToPoll = new HashSet<>();
		for (ActiveLiveGame activeGame : activeGames.values()) {
			if (activeGame.leagueName() != null && !activeGame.leagueName().isBlank()) {
				leaguesToPoll.add(activeGame.leagueName());
			}
		}
		leaguesToPoll.addAll(
				leagueMatchService.findLeaguesWithMatchesBetween(nowUtc.minusDays(1), nowUtc.plusDays(1)));

		for (String league : leaguesToPoll) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				for (MatchResultDto match : response.getMatches()) {
					boolean activeOrRecentlyActive = "inProgress".equalsIgnoreCase(match.getState())
							|| activeMatchIds.contains(match.getMatchId());
					if (!activeOrRecentlyActive) {
						continue;
					}
					scheduleCacheDirty |= leagueMatchService.syncRealtimeMatchStatus(match, league);

					if (match.getLiveGameIds() == null) {
						continue;
					}
					for (String gameId : match.getLiveGameIds()) {
						if (gameId == null || gameId.isBlank()) {
							continue;
						}
						discoveredGameIds.add(gameId);
						ActiveLiveGame current = activeGames.get(gameId);
						String resolvedLeagueName = (match.getLeagueName() == null || match.getLeagueName().isBlank())
								? league
								: match.getLeagueName();
						// 세트마다 진영(블루/레드)이 스왑되므로 매치 기준 팀명/팀ID 만으로는 진영을 단정할 수 없다.
						// SET_START 는 'A vs B' 매치 단위 프레이밍이라 매치 기준 esportsTeamId 로 충분하다.
						Integer setNumber = resolveSetNumber(match, gameId);
						String blueExternalId = match.getBlueTeam() != null ? match.getBlueTeam().getExternalTeamId() : null;
						String redExternalId = match.getRedTeam() != null ? match.getRedTeam().getExternalTeamId() : null;
						ActiveLiveGame next = new ActiveLiveGame(
								gameId,
								match.getMatchId(),
								resolvedLeagueName,
								match.getBlueTeam() != null ? match.getBlueTeam().getName() : null,
								match.getRedTeam() != null ? match.getRedTeam().getName() : null,
								nowUtc,
								current != null ? current.consecutiveFailures() : 0,
								setNumber,
								blueExternalId,
								redExternalId);
						activeGames.put(gameId, next);
						liveGameMetadataService.remember(next);

						if (liveNotificationEnabled && current == null && isNotifiableLeague(resolvedLeagueName)) {
							log.info("[live-notify] match-start league={} {} vs {} gameId={}",
									resolvedLeagueName, next.blueTeamName(), next.redTeamName(), gameId);
							notificationService.sendLiveMatchNotification(
									resolvedLeagueName,
									next.blueTeamName(),
									next.redTeamName(),
									gameId,
									match.getMatchId());
						}

						// [FCM #21] SET_START 푸시. live.notification.fcm.enabled 플래그로 게이트.
						// 디스코드 알림과 같은 'current == null'(새 세트 최초 발견) 조건에서만 1회 발송.
						if (teamLiveEventPushService.isEnabled() && current == null
								&& isNotifiableLeague(resolvedLeagueName)) {
							teamLiveEventPushService.notifyMatchEvent(
									com.toy.nar.app.mobile.push.TeamLiveEventPushService.TYPE_SET_START,
									match.getMatchId(),
									setNumber != null ? setNumber : 0,
									blueExternalId,
									redExternalId,
									next.blueTeamName(),
									next.redTeamName());
						}
					}
				}
			} catch (Exception e) {
				log.warn("Live discovery failed for league {}: {}", league, e.getMessage());
			}
		}

		if (scheduleCacheDirty) {
			cacheEvictionService.evictScheduleCaches();
		}

		List<String> toRemove = new ArrayList<>();
		for (ActiveLiveGame activeGame : activeGames.values()) {
			boolean notDiscovered = !discoveredGameIds.contains(activeGame.gameId());
			boolean stale = activeGame.lastSeenAtUtc() != null
					&& java.time.Duration.between(activeGame.lastSeenAtUtc(), nowUtc).toMillis() > staleThresholdMs;

			// [FCM #21] SET_END 감지(신규). 직전까지 활성이던 gameId 가 이번 사이클 피드의 라이브 세트에서
			// 빠지면(=notDiscovered) 세트 종료로 본다. 플래그 게이트 + dedup 으로 1회만 발송된다.
			// 플래그가 꺼져 있으면 어떤 부작용도 없다(기존 cleanup 동작과 바이트 동일).
			if (notDiscovered && teamLiveEventPushService.isEnabled()
					&& isNotifiableLeague(activeGame.leagueName())) {
				fireSetEndNotification(activeGame);
			}

			if (notDiscovered && stale) {
				toRemove.add(activeGame.gameId());
			}
		}

		toRemove.forEach(gameId -> {
			liveStateStore.removeGame(gameId);
			liveObjectEventRecorder.evict(gameId);
		});
	}

	/** gameId 의 매치 내 1-based 세트 번호. match.gameIds 의 순서가 세트 순서다. 못 찾으면 null. */
	private Integer resolveSetNumber(MatchResultDto match, String gameId) {
		if (match.getGameIds() == null) {
			return null;
		}
		int index = match.getGameIds().indexOf(gameId);
		return index < 0 ? null : index + 1;
	}

	/** [FCM #21] SET_END 푸시. 매치 단위 프레이밍이라 매치 기준 esportsTeamId 로 양 팀 구독자에게 발송(dedup 1회). */
	private void fireSetEndNotification(ActiveLiveGame activeGame) {
		try {
			teamLiveEventPushService.notifyMatchEvent(
					com.toy.nar.app.mobile.push.TeamLiveEventPushService.TYPE_SET_END,
					activeGame.matchId(),
					activeGame.setNumber() != null ? activeGame.setNumber() : 0,
					activeGame.blueEsportsTeamId(),
					activeGame.redEsportsTeamId(),
					activeGame.blueTeamName(),
					activeGame.redTeamName());
		} catch (Exception e) {
			log.warn("[live-notify] set-end FCM failed gameId={} matchId={}: {}",
					activeGame.gameId(), activeGame.matchId(), e.getMessage());
		}
	}

	private Set<String> activeMatchIds(Map<String, ActiveLiveGame> activeGames) {
		Set<String> matchIds = new HashSet<>();
		for (ActiveLiveGame activeGame : activeGames.values()) {
			if (activeGame.matchId() != null && !activeGame.matchId().isBlank()) {
				matchIds.add(activeGame.matchId());
			}
		}
		return matchIds;
	}

	@Scheduled(fixedDelayString = "${lolesports.live.poll-interval-ms:5000}")
	public void pollActiveGames() {
		List<ActiveLiveGame> activeGames = new ArrayList<>(liveStateStore.getActiveGames().values());
		if (activeGames.isEmpty()) {
			return;
		}

		for (ActiveLiveGame activeGame : activeGames) {
			try {
				String startingTime = computeStartingTime(activeGame.gameId());
				JsonNode window = liveStatsClient.getWindow(activeGame.gameId(), startingTime);
				JsonNode details = liveStatsClient.getDetails(activeGame.gameId(), startingTime);
				liveFrameProcessor.process(activeGame, window, details);
			} catch (Exception e) {
				ActiveLiveGame failed = activeGame.increaseFailures();
				if (failed.consecutiveFailures() >= maxConsecutiveFailures) {
					log.warn("Removing game {} after {} consecutive polling failures", failed.gameId(), failed.consecutiveFailures());
					liveStateStore.removeGame(failed.gameId());
					liveObjectEventRecorder.evict(failed.gameId());
					continue;
				}
				liveStateStore.getActiveGames().put(failed.gameId(), failed);
				log.warn("Live polling failed for game {}: {}", failed.gameId(), e.getMessage());
			}
		}
	}

	private String computeStartingTime(String gameId) {
		Instant now = Instant.now();
		Instant candidate = liveStateStore.getLatestState(gameId)
				.map(state -> nextWindowStart(state.frameTimestampUtc().toInstant(ZoneOffset.UTC)))
				.orElseGet(() -> now.minusSeconds(INITIAL_LOOKBACK_SECONDS));

		// 뒤처졌으면 라이브 엣지로 점프 (분 단위 catch-up 지연 방지)
		Instant liveEdgeFloor = now.minusSeconds(MAX_LAG_SECONDS);
		if (candidate.isBefore(liveEdgeFloor)) {
			candidate = liveEdgeFloor;
		}
		// 피드 20초 룰: 너무 최신 window는 거부되므로 상한을 둔다
		Instant newestAllowed = now.minusSeconds(MIN_FEED_AGE_SECONDS);
		if (candidate.isAfter(newestAllowed)) {
			candidate = newestAllowed;
		}

		long flooredSeconds = (candidate.getEpochSecond() / 10) * 10;
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(flooredSeconds), ZoneOffset.UTC).format(START_TIME_FORMATTER);
	}

	/** 알림 대상 리그인지 (notificationLeagues 설정, 기본 LCK). 그 외 리그는 디스코드 알림 안 보냄. */
	private boolean isNotifiableLeague(String league) {
		if (league == null || league.isBlank()) {
			return false;
		}
		for (String allowed : notificationLeagues.split(",")) {
			if (allowed.trim().equalsIgnoreCase(league.trim())) {
				return true;
			}
		}
		return false;
	}

	private Instant nextWindowStart(Instant latestFrameTimestamp) {
		long nextWindowSecond = ((latestFrameTimestamp.getEpochSecond() / 10) + 1) * 10;
		return Instant.ofEpochSecond(nextWindowSecond);
	}
}
