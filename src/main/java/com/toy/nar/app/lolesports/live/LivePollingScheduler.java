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
	private final com.toy.nar.app.lolesports.LeagueConfigService leagueConfigService;
	private final CacheEvictionService cacheEvictionService;
	private final NotificationService notificationService;
	private final com.toy.nar.app.mobile.push.TeamLiveEventPushService teamLiveEventPushService;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.stale-threshold-ms:180000}")
	private long staleThresholdMs;

	/**
	 * SET_END 푸시를 이미 보낸 gameId. notDiscovered 는 stale 제거 전까지 매 사이클 참이라
	 * 여기서 1회로 제한한다(같은 세트 종료 반복 발송 방지). 게임이 피드에 다시 나타나면
	 * 오탐(일시 누락)으로 보고 해제한다.
	 */
	private final java.util.Set<String> setEndNotifiedGameIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * livestats 프레임 gameState=finished 로 종료를 확정한 gameId.
	 * 업스트림 eventDetails 는 종료된 세트를 분 단위(최악: 다음 세트 끝까지) inProgress 로 방치하므로,
	 * 프레임 신호가 1차 종료 판정이고 notDiscovered 는 fallback 이다.
	 * 여기 있는 gameId 는 디스커버리에 다시 나타나도 SET_END dedup 을 해제하지 않는다(재발송 방지).
	 */
	private final java.util.Set<String> frameFinishedGameIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * 시작 알림(디스코드+SET_START 푸시)을 이미 보낸 gameId. 업스트림 eventDetails 는
	 * 픽밴 시작 시점에 다음 게임을 inProgress 로 뒤집으므로 디스커버리 등장은 "픽밴 시작"이지
	 * 게임 시작이 아니다. 실제 시작 신호는 livestats 첫 프레임 도착이고, 여기서 1회로 제한한다.
	 */
	private final java.util.Set<String> startNotifiedGameIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.max-consecutive-failures:6}")
	private int maxConsecutiveFailures;

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.notification.enabled:false}")
	private boolean liveNotificationEnabled;

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

		// 백오피스 리그 설정(live_enabled=false)이면 진행 중 게임 포함 수집 자체를 중단한다.
		Set<String> liveEnabledLeagues = new HashSet<>(leagueConfigService.liveLeagues());
		leaguesToPoll.removeIf(league -> !liveEnabledLeagues.contains(league.toUpperCase()));

		for (String league : leaguesToPoll) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				for (MatchResultDto match : response.getMatches()) {
					// 업스트림(lolesports)이 EWC 등 일부 대회는 라이브 중에도 경기 state 를 unstarted 로 방치한다.
					// 스케줄 state 로 못 잡으므로, 시작 시각이 지난 unstarted 경기는 매 사이클 livestats 피드를 직접 찔러
					// 진행 중 게임을 찾는다. 피드가 라이브면 state 를 inProgress 로 올린다.
					// 매 사이클 재판정해야 한다 — 이미 추적 중(activeMatchIds)이어도 여기서 다시 올리지 않으면
					// 아래 syncRealtimeMatchStatus 가 Riot 원본 unstarted 로 DB 를 되돌린다.
					List<String> feedLiveGameIds = List.of();
					if ("unstarted".equalsIgnoreCase(match.getState())) {
						FeedProbe probe = probeFeed(match);
						feedLiveGameIds = probe.liveGameIds();
						if (!feedLiveGameIds.isEmpty()) {
							match.setState("inProgress");
						} else if (probe.sawFinished()) {
							// 세트 사이/경기 종료 직후: 피드는 finished 잔상인데 업스트림 state 는 여전히 unstarted.
							// 여기서 sync 하면 matchId 가 activeMatchIds 에 남아 있는 동안(stale 3분 창)
							// Riot 원본 unstarted 가 DB 의 inProgress 를 되돌린다. 이 사이클은 건너뛰고
							// 업스트림 completed flip(또는 30분 cron)에 맡긴다.
							continue;
						}
					}

					// 업스트림 completed flip 이 stale 창(3분)을 넘겨 도착하면 추적이 이미 끝나
					// activeMatchIds 로는 못 잡는다(EWC 상습 — 그러면 30분 cron 까지 inProgress 방치).
					// 최근 경기(시작 −5분 ~ +6시간) completed 는 추적 여부와 무관하게 sync 한다.
					// upsert 가 무변경 skip 이라 이미 completed 인 매치는 write 가 발생하지 않는다.
					boolean recentlyCompleted = "completed".equalsIgnoreCase(match.getState())
							&& withinFeedProbeWindow(match.getMatchDate());
					boolean activeOrRecentlyActive = "inProgress".equalsIgnoreCase(match.getState())
							|| activeMatchIds.contains(match.getMatchId())
							|| recentlyCompleted;
					if (!activeOrRecentlyActive) {
						continue;
					}
					scheduleCacheDirty |= leagueMatchService.syncRealtimeMatchStatus(match, league);

					List<String> liveGameIds = !feedLiveGameIds.isEmpty()
							? feedLiveGameIds
							: match.getLiveGameIds();
					if (liveGameIds == null) {
						continue;
					}
					for (String gameId : liveGameIds) {
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
						// 피드에 다시 나타났으면 SET_END 오탐(일시 누락)이었으므로 재발송 가능하게 해제.
						// 단 프레임 finished 로 확정된 게임은 예외 — eventDetails 가 종료를 늦게 반영해
						// inProgress 로 계속 보이는 것뿐이므로 dedup 을 유지한다.
						if (!frameFinishedGameIds.contains(gameId)) {
							setEndNotifiedGameIds.remove(gameId);
						}
						liveGameMetadataService.remember(next);
						// 시작 알림은 여기(디스커버리)가 아니라 pollActiveGames 의 첫 프레임 관측에서 쏜다.
						// 디스커버리 등장 = 픽밴 시작이라, 여기서 쏘면 "이전 세트 종료 몇 분 뒤" 오탐이 된다.
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

			// [FCM #21] SET_END 폴백. 1차 판정은 프레임 gameState=finished(pollActiveGames)이고,
			// 여기는 프레임 신호를 놓친 채 게임이 피드에서 사라진 경우의 안전망이다.
			// stale(기본 3분) 확정 전에 쏘면 픽밴 중 디스커버리 플랩으로 오탐 발송되고,
			// DB dedup 키가 소진돼 진짜 종료가 무음 스킵된다(2026-07-12 MSI 결승 실사례).
			if (notDiscovered && stale && teamLiveEventPushService.isEnabled()
					&& isNotifiableLeague(activeGame.leagueName())
					&& setEndNotifiedGameIds.add(activeGame.gameId())) {
				fireSetEndNotification(activeGame);
			}

			if (notDiscovered && stale) {
				toRemove.add(activeGame.gameId());
			}
		}

		toRemove.forEach(gameId -> {
			liveStateStore.removeGame(gameId);
			liveObjectEventRecorder.evict(gameId);
			setEndNotifiedGameIds.remove(gameId);
			frameFinishedGameIds.remove(gameId);
			startNotifiedGameIds.remove(gameId);
		});
	}

	/** livestats 피드로 라이브를 직접 판정할 시작 시각 창(분). 방송/피드 오차를 흡수한다. */
	private static final long FEED_PROBE_LEAD_MINUTES = 5L;
	private static final long FEED_PROBE_TRAIL_HOURS = 6L;

	/** unstarted 재판정 프로브 결과: 라이브 게임 id 목록 + finished 잔상 관측 여부. */
	private record FeedProbe(List<String> liveGameIds, boolean sawFinished) {
		static final FeedProbe EMPTY = new FeedProbe(List.of(), false);
	}

	/**
	 * 업스트림 state 가 unstarted 인 경기를 livestats 피드로 재판정한다.
	 * 시작 시각 창 안(−5분 ~ +6시간)의 경기에 한해 게임 id 를 순회하며 window 를 찔러,
	 * 최근 프레임이 있고 finished 가 아닌(=in_game) 게임 id 목록과 finished 관측 여부를 돌려준다.
	 * finished 관측은 "시작 전"이 아니라 "세트 사이/종료 직후" 신호라 unstarted sync 차단에 쓴다.
	 * 창 밖이거나 게임 id 가 없거나 피드가 비면 EMPTY — 기존 inProgress 경로에는 영향 없다.
	 */
	private FeedProbe probeFeed(MatchResultDto match) {
		if (!"unstarted".equalsIgnoreCase(match.getState()) || !withinFeedProbeWindow(match.getMatchDate())) {
			return FeedProbe.EMPTY;
		}
		List<String> gameIds = match.getGameIds();
		if (gameIds == null || gameIds.isEmpty()) {
			return FeedProbe.EMPTY;
		}
		List<String> live = new ArrayList<>();
		boolean sawFinished = false;
		for (String gameId : gameIds) {
			if (gameId == null || gameId.isBlank()) {
				continue;
			}
			try {
				// startingTime 은 반드시 유효한 값이어야 한다 — null 이면 빈 쿼리파라미터로 나가 피드가 거부한다.
				JsonNode window = liveStatsClient.getWindow(gameId, computeStartingTime(gameId));
				if (!hasFrames(window)) {
					continue;
				}
				if (isFrameFinished(window)) {
					sawFinished = true;
				} else {
					live.add(gameId);
				}
			} catch (Exception e) {
				log.debug("livestats 라이브 프로브 실패 gameId={}: {}", gameId, e.getMessage());
			}
		}
		return new FeedProbe(live, sawFinished);
	}

	/** matchDate(ISO instant)가 지금 기준 시작 시각 창 안인지. 파싱 실패 시 false. */
	private boolean withinFeedProbeWindow(String matchDate) {
		if (matchDate == null || matchDate.isBlank()) {
			return false;
		}
		try {
			Instant start = Instant.parse(matchDate);
			Instant now = Instant.now();
			return !now.isBefore(start.minus(Duration.ofMinutes(FEED_PROBE_LEAD_MINUTES)))
					&& !now.isAfter(start.plus(Duration.ofHours(FEED_PROBE_TRAIL_HOURS)));
		} catch (Exception e) {
			return false;
		}
	}

	/** window 응답에 프레임이 하나라도 있는지. 픽밴/로딩 중엔 프레임이 없다. */
	private boolean hasFrames(JsonNode windowResponse) {
		return windowResponse != null
				&& windowResponse.path("frames").isArray()
				&& !windowResponse.path("frames").isEmpty();
	}

	/**
	 * 세트 시작 알림(디스코드 + SET_START 푸시). 첫 프레임 관측 시 1회.
	 * 알림 실패가 폴링 실패로 집계되지 않게 여기서 삼킨다.
	 */
	private void fireSetStartNotification(ActiveLiveGame activeGame) {
		log.info("[live-notify] set-start(frame) league={} {} vs {} gameId={} matchId={} set={}",
				activeGame.leagueName(), activeGame.blueTeamName(), activeGame.redTeamName(),
				activeGame.gameId(), activeGame.matchId(), activeGame.setNumber());
		try {
			if (liveNotificationEnabled) {
				notificationService.sendLiveMatchNotification(
						activeGame.leagueName(),
						activeGame.blueTeamName(),
						activeGame.redTeamName(),
						activeGame.gameId(),
						activeGame.matchId());
			}
			if (teamLiveEventPushService.isEnabled()) {
				teamLiveEventPushService.notifyMatchEvent(
						com.toy.nar.app.mobile.push.TeamLiveEventPushService.TYPE_SET_START,
						activeGame.matchId(),
						activeGame.setNumber() != null ? activeGame.setNumber() : 0,
						activeGame.blueEsportsTeamId(),
						activeGame.redEsportsTeamId(),
						activeGame.blueTeamName(),
						activeGame.redTeamName());
			}
		} catch (Exception e) {
			log.warn("[live-notify] set-start failed gameId={} matchId={}: {}",
					activeGame.gameId(), activeGame.matchId(), e.getMessage());
		}
	}

	/** window 응답의 마지막 프레임이 gameState=finished 인지. 프레임이 없으면 false. */
	private boolean isFrameFinished(JsonNode windowResponse) {
		if (windowResponse == null) {
			return false;
		}
		JsonNode frames = windowResponse.path("frames");
		if (!frames.isArray() || frames.isEmpty()) {
			return false;
		}
		return "finished".equalsIgnoreCase(frames.get(frames.size() - 1).path("gameState").asText());
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

				// 세트 시작 판정: livestats 첫 프레임 도착 = 실제 인게임 시작.
				// 첫 관측이 이미 finished 면(재기동 등) 시작 알림은 건너뛴다.
				if (hasFrames(window) && !isFrameFinished(window)
						&& isNotifiableLeague(activeGame.leagueName())
						&& startNotifiedGameIds.add(activeGame.gameId())) {
					fireSetStartNotification(activeGame);
				}

				// 세트 종료 1차 판정: 프레임 gameState=finished (실제 종료 후 수 초 내 반영).
				// finished 프레임은 timestamp 가 멈춰 aggregator 의 stale 필터에 걸리므로 여기 raw 응답에서 본다.
				if (isFrameFinished(window)) {
					// store 에 마킹해야 세트 상태(LIVE/ENDED)가 stale 3분 잔상 동안 LIVE 로 남지 않는다.
					// 푸시 게이트(isEnabled/리그)와 무관하게 마킹한다.
					liveStateStore.markFinished(activeGame.gameId());
					if (frameFinishedGameIds.add(activeGame.gameId())
							&& teamLiveEventPushService.isEnabled()
							&& isNotifiableLeague(activeGame.leagueName())
							&& setEndNotifiedGameIds.add(activeGame.gameId())) {
						log.info("[live-notify] set-end(frame) gameId={} matchId={} set={}",
								activeGame.gameId(), activeGame.matchId(), activeGame.setNumber());
						fireSetEndNotification(activeGame);
					}
				}

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

	/** 알림 대상 리그인지 (백오피스 리그 설정 notification_enabled). 그 외 리그는 데이터 수집만 하고 알림은 보내지 않는다. */
	private boolean isNotifiableLeague(String league) {
		return leagueConfigService.isNotificationEnabled(league);
	}

	private Instant nextWindowStart(Instant latestFrameTimestamp) {
		long nextWindowSecond = ((latestFrameTimestamp.getEpochSecond() / 10) + 1) * 10;
		return Instant.ofEpochSecond(nextWindowSecond);
	}
}
