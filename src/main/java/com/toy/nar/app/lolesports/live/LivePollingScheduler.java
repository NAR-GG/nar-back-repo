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
	private final com.toy.nar.app.mobile.push.LiveActivityPushService liveActivityPushService;
	private final com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository teamExternalIdentityRepository;
	private final LiveFrameStallTracker frameStallTracker;
	private final com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository;
	@org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")
	private final java.util.concurrent.Executor applicationTaskExecutor;

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
	 * 네이버 종료 확정으로 이미 completed 를 쓴 matchId. 업스트림 flip 이 오기까지(실측 17분+)
	 * 매 사이클 네이버를 다시 찌르지 않게 막는다. 확정 실패(세트 사이)면 등록하지 않아 다음 사이클에 재시도한다.
	 * ponytail: 프로세스 생애 동안 누적된다 — 하루 수백 건이라 무해. 커지면 stale 제거 시점에 같이 비운다.
	 * 재기동으로 이 집합이 비면 이미 completed 인 매치에 대해 flip 까지 네이버를 다시 찌른다(최대 ~17분,
	 * 10초 주기). 콜 수가 문제되면 DB state 조회로 게이트한다.
	 */
	private final java.util.Set<String> naverFinalizedMatchIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * 시작 알림(디스코드+SET_START 푸시)을 이미 보낸 gameId. 업스트림 eventDetails 는
	 * 픽밴 시작 시점에 다음 게임을 inProgress 로 뒤집으므로 디스커버리 등장은 "픽밴 시작"이지
	 * 게임 시작이 아니다. 실제 시작 신호는 livestats 첫 프레임 도착이고, 여기서 1회로 제한한다.
	 */
	private final java.util.Set<String> startNotifiedGameIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * 세트 종료 카드로 마지막에 보낸 스코어 {blue, red}. 폴링 tick 마다 같은 값을 다시 쏘거나
	 * 후퇴한 값을 쏘지 않게 한다.
	 * ponytail: {@link #naverFinalizedMatchIds} 와 같은 이유로 프로세스 생애 동안 누적된다 — 하루 수십 건이라 무해.
	 */
	private final java.util.Map<String, int[]> lastSetEndCardScoreByGame =
			new java.util.concurrent.ConcurrentHashMap<>();

	// 매치 종료 카드 발송 여부는 LiveActivityPushService 가 공유 상태로 관리한다
	// (matchEndPushed/claimMatchEndPush) — 스윕까지 발송자가 셋이라 여기 두면 스윕 발송이 안 보인다.

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
					// 업스트림이 진행 중 게임을 못 알려주는 경우를 피드 실측으로 우회한다. 두 가지가 있다:
					//  (1) state 를 unstarted 로 방치 (EWC 등)
					//  (2) state 는 넘어갔는데 liveGameIds 가 비어서 도착 — 실측 15~16분 지연
					//      (2026-07-27 KESPA T1 vs DNS, 2026-07-28 KESPA DNS vs BRO 3세트)
					// (2)는 예전에 unstarted 게이트에 걸려 우회로를 못 탔고, 그 사이 세트 하나가
					// 통째로 추적되지 않았다. 그래서 게이트를 "업스트림이 라이브 게임을 모를 때"로 넓힌다.
					// completed 는 디스커버리 대상이 아니므로 제외해 불필요한 외부 호출을 막는다.
					// 네이버 종료 확정으로 이미 completed 를 쓴 매치는 업스트림 flip 이 올 때까지 손대지 않는다.
					// 프로브도 sync 도 스킵한다 — 업스트림 원본(inProgress/unstarted)으로 sync 하면
					// DB 의 completed 가 되돌아간다. flip 이 도착하면 state 가 completed 라 이 게이트를 지나
					// 아래 recentlyCompleted 경로로 최종 스코어가 덮어써진다(self-heal).
					if (naverFinalizedMatchIds.contains(match.getMatchId())
							&& !"completed".equalsIgnoreCase(match.getState())) {
						continue;
					}

					// 추적 중인 세트가 전부 프레임 finished 로 확정됐는데 업스트림 state 는 아직 inProgress 인 구간.
					// 업스트림 completed flip 은 실측 4분 50초~16분 늦게 온다(LCK 6경기, 2026-08-05~07 CloudWatch).
					// 네이버는 종료 후 ~1.5분에 matchStatus=RESULT 를 주므로 그걸로 먼저 확정한다.
					// 세트 사이면 네이버가 아직 RESULT 가 아니라 false 를 돌려주고 기존 경로가 그대로 돈다.
					// 프레임 신호를 트리거로 쓰는 이유: probeFeed 는 업스트림이 liveGameIds 를 비웠거나
					// 프레임이 180초 정지해야 돌아서 그만큼 확정이 늦다.
					// ponytail: 세트 사이엔 이 호출과 overlayNaverScoreIfAhead 가 같은 day 응답을 각각 받아온다
					// (10초 주기 2콜). 세트 간격이 짧아 무해 — 콜 수가 문제되면 사이클 단위로 캐시한다.
					if (!"completed".equalsIgnoreCase(match.getState())
							&& allTrackedGamesFinished(activeGames, match.getMatchId())
							&& leagueMatchService.syncCompletedMatchFromNaver(match, league)) {
						naverFinalizedMatchIds.add(match.getMatchId());
						scheduleCacheDirty = true;
						continue;
					}

					List<String> feedLiveGameIds = List.of();
					boolean upstreamKnowsLiveGames =
							match.getLiveGameIds() != null && !match.getLiveGameIds().isEmpty();
					// liveGameIds 가 차 있어도 그 게임의 프레임이 얼어 있으면(다음 세트로 못 넘어간
					// 낡은 값 — 2026-07-30 HLE vs DK: 세트3 피드가 살아난 뒤에도 업스트림이 세트2 를
					// 계속 가리킴) 매치의 전체 gameId 를 직접 찔러 다음 세트를 찾는다.
					boolean trackedGameStalled = hasStalledTrackedGame(activeGames, match.getMatchId());
					if ((!upstreamKnowsLiveGames || trackedGameStalled)
							&& !"completed".equalsIgnoreCase(match.getState())) {
						if (trackedGameStalled && upstreamKnowsLiveGames) {
							log.info("[live-discovery] 추적 중 게임 프레임 정지 — 피드 직접 프로브 matchId={}",
									match.getMatchId());
						}
						FeedProbe probe = probeFeed(match);
						feedLiveGameIds = probe.liveGameIds();
						if (!feedLiveGameIds.isEmpty()) {
							match.setState("inProgress");
						} else if ("unstarted".equalsIgnoreCase(match.getState()) && probe.sawFinished()) {
							// 세트 사이/경기 종료 직후: 피드는 finished 잔상인데 업스트림 state 는 여전히 unstarted.
							// 업스트림 원본(unstarted)으로 sync 하면 DB 의 inProgress 가 되돌아가므로 쓸 수 없다.
							// 대신 네이버가 매치 종료(RESULT)를 확인해주면 completed 를 직접 확정한다 —
							// 업스트림 flip 은 실측 17분+ 늦게 온다(2026-07-27 KESPA T1 vs DNS).
							// 네이버가 아직 진행 중이면(세트 사이) 기존대로 이 사이클을 건너뛴다.
							if (!naverFinalizedMatchIds.contains(match.getMatchId())
									&& leagueMatchService.syncCompletedMatchFromNaver(match, league)) {
								naverFinalizedMatchIds.add(match.getMatchId());
								scheduleCacheDirty = true;
							}
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
						if (current == null) {
							// 조사 때마다 디스커버리가 뭘 봤는지 로그가 없어 피드를 수동 조회해야 했다.
							// 신규 편입은 세트당 1회라 스팸이 아니다.
							log.info("[live-discovery] 신규 추적 gameId={} matchId={} state={} feedProbe={}",
									gameId, match.getMatchId(), match.getState(), !feedLiveGameIds.isEmpty());
						}
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
			frameStallTracker.evict(gameId);
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
		// state 조건은 호출부가 판단한다(unstarted 방치 + liveGameIds 지연 둘 다 대상).
		if (!withinFeedProbeWindow(match.getMatchDate())) {
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
	 *
	 * <p>SET_END 와 마찬가지로 폴링 스레드가 아니라 executor 에서 보낸다. 디스코드 웹훅과 FCM 발송은
	 * 외부 호출이고 DB 락 대기에도 걸린다 — 실측 2026-07-29 T1 vs KT 에서 이 발송이 락 대기 50초
	 * (innodb_lock_wait_timeout)에 걸려 폴링 스레드를 세웠다. 폴링이 멈춘 사이 computeStartingTime 이
	 * 라이브 엣지로 점프해(MAX_LAG_SECONDS) 그 구간 프레임을 영구히 건너뛰었고, 재개 시 19분 점프로
	 * 관측 공백 가드에 걸려 그 구간 이벤트 알림이 전부 누락됐다.</p>
	 */
	private void fireSetStartNotification(ActiveLiveGame activeGame) {
		log.info("[live-notify] set-start(frame) league={} {} vs {} gameId={} matchId={} set={}",
				activeGame.leagueName(), activeGame.blueTeamName(), activeGame.redTeamName(),
				activeGame.gameId(), activeGame.matchId(), activeGame.setNumber());
		applicationTaskExecutor.execute(() -> {
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
			// 디스코드·FCM 실패가 카드 갱신까지 막지 않도록 try 밖에서 부른다(SET_END 와 동일).
			pushLiveActivitySetStart(activeGame);
		});
	}

	/**
	 * iOS Live Activity 카드를 진행 중으로 바꾼다. 앱 폴링은 백그라운드에서 멈추므로
	 * 잠금화면 카드는 이 푸시로만 갱신된다. 알림 흐름을 깨지 않게 실패를 흡수한다.
	 */
	private void pushLiveActivitySetStart(ActiveLiveGame activeGame) {
		if (!liveActivityPushService.isEnabled()) {
			return;
		}
		try {
			var match = leagueMatchRepository.findById(activeGame.matchId()).orElse(null);
			int setNumber = activeGame.setNumber() != null ? activeGame.setNumber() : 0;
			Integer blueScore = match == null ? null : match.getBlueScore();
			Integer redScore = match == null ? null : match.getRedScore();

			// 이미 카드를 띄운 사람들 갱신.
			liveActivityPushService.notifySetStart(activeGame.matchId(), setNumber, blueScore, redScore);

			// 아직 카드가 없는 구독자에게는 카드를 새로 만들어 준다.
			// 세트마다 진영이 스왑되므로 팀 표기는 세트 기준(ActiveLiveGame)이 아니라
			// 매치 기준(LeagueMatch)을 쓴다 — 앱도 매치 기준으로 A/B 를 잡는다.
			if (match != null) {
				liveActivityPushService.startCards(
						activeGame.matchId(), setNumber, blueScore, redScore,
						resolveTeamId(match.getBlueExternalTeamId()),
						resolveTeamId(match.getRedExternalTeamId()),
						new com.toy.nar.app.mobile.push.LiveActivityPushService.MatchCardAttributes(
								match.getId(),
								match.getBlueTeamName(), match.getBlueTeamCode(),
								match.getRedTeamName(), match.getRedTeamCode(),
								match.getLeagueName()));
			}
		} catch (Exception e) {
			log.warn("[live-activity] set-start 실패 matchId={}: {}", activeGame.matchId(), e.getMessage());
		}
	}

	/** 팀 구독 매칭용 내부 팀 id. 해석 실패하면 null 이고, 그 팀 구독자는 대상에서 빠진다. */
	private Long resolveTeamId(String externalTeamId) {
		if (externalTeamId == null || externalTeamId.isBlank()) {
			return null;
		}
		try {
			return teamExternalIdentityRepository
					.findBySourceAndExternalTeamId("LOLESPORTS", externalTeamId)
					.map(identity -> identity.getTeam().getId())
					.orElse(null);
		} catch (Exception e) {
			log.warn("[live-activity] 팀 id 해석 실패 externalTeamId={}: {}", externalTeamId, e.getMessage());
			return null;
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

	/**
	 * [FCM #21] SET_END 푸시. 매치 단위 프레이밍이라 매치 기준 esportsTeamId 로 양 팀 구독자에게 발송(dedup 1회).
	 * 스코어 라인이 업스트림 재시도(수 초 sleep)를 할 수 있어 폴링 스레드가 아니라 executor 에서 보낸다.
	 */
	private void fireSetEndNotification(ActiveLiveGame activeGame) {
		applicationTaskExecutor.execute(() -> {
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
		});
	}


	/**
	 * 종료된 세트의 카드를 현재 스코어로 유지한다 — 발송은 스코어가 바뀔 때만.
	 *
	 * <p>예전엔 세트 종료 카드를 SET_END FCM 뒤에 한 번만 보냈다. 그 FCM 은 스코어 재조회(네이버·
	 * 업스트림)를 최대 6회×10초 기다리므로 카드까지 그 대기에 묶였고, 발송된 뒤에는 갱신 경로가
	 * 없어 값이 고착했다 — 실측 2026-08-14 DK vs T1 세트1: 프레임 종료 감지 17:39:47,
	 * 카드 17:41:51(124초). 앱 상세 화면은 DB 를 폴링해 17:41:36 에 이미 맞은 값을 보여
	 * 카드만 뒤처져 보였다.</p>
	 *
	 * <p>대신 프레임 finished 로 확정된 세트를 폴링 tick 마다 훑는다. 감지된 tick 에서 바로 한 번
	 * 나가고(스코어가 아직 직전 세트 값이어도 "다음 세트 준비 중" 은 즉시 맞다), 이후 스코어가
	 * 도착하면 그 tick 에서 카드가 따라간다. 재조회 대기가 카드를 붙잡지 않는다.</p>
	 *
	 * <p>다전제 승리 조건에 닿으면 매치 종료로 한 번 더 보낸다(claim 으로 1회 보장). 카드 진행도
	 * 워터마크가 setEnded(1) → matchEnded(2) 상승만 허용하므로 순서가 뒤집히는 발송은 없고,
	 * 같은 단계 재발송은 스코어 갱신을 위해 통과시킨다.</p>
	 */
	private void refreshEndedSetCard(ActiveLiveGame activeGame) {
		if (!liveActivityPushService.isEnabled()
				|| activeGame.matchId() == null
				|| liveActivityPushService.matchEndPushed(activeGame.matchId())) {
			return;
		}
		try {
			var match = leagueMatchRepository.findById(activeGame.matchId()).orElse(null);
			if (match == null) {
				return;
			}
			int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
			int red = match.getRedScore() == null ? 0 : match.getRedScore();
			int setNumber = activeGame.setNumber() != null ? activeGame.setNumber() : 0;
			boolean matchEnded = isMatchEnded(match.getBestOf(), blue, red);

			if (matchEnded) {
				// claim 으로 검사와 등록을 한 번에 한다 — 스윕과 동시에 들어올 수 있다.
				if (!liveActivityPushService.claimMatchEndPush(activeGame.matchId())) {
					return;
				}
				String winner = blue > red ? match.getBlueTeamCode() : match.getRedTeamCode();
				log.info("[live-activity] 매치 종료 카드 matchId={} set={} score={}:{}",
						activeGame.matchId(), setNumber, blue, red);
				// 폴링 스레드에서 APNs 팬아웃을 태우면 다음 프레임 수집이 밀린다.
				applicationTaskExecutor.execute(() -> liveActivityPushService.notifySetEnd(
						activeGame.matchId(), setNumber, blue, red, true, winner));
				return;
			}

			// 매 tick 도는 자리라 발송 조건을 좁힌다(폴링은 단일 스레드라 get→put 사이 경합이 없다).
			int[] previous = lastSetEndCardScoreByGame.get(activeGame.gameId());
			if (previous != null) {
				// 같은 스코어를 다시 쏘면 APNs 왕복만 늘어난다.
				if (previous[0] == blue && previous[1] == red) {
					return;
				}
				// 후퇴는 무시한다. 세트가 끝난 뒤 스코어가 줄어들 이유가 없고, 30분 전체 sync 가
				// 진행 중 경기를 업스트림 stale 값으로 되돌리는 구간이 있다(isResultRegression 은
				// completed 만 지킨다). 그걸 카드에 반영하면 잠금화면 숫자가 왕복한다.
				if (blue + red < previous[0] + previous[1]) {
					log.info("[live-activity] 세트 종료 카드 스코어 후퇴 무시 matchId={} {}:{} -> {}:{}",
							activeGame.matchId(), previous[0], previous[1], blue, red);
					return;
				}
			}
			lastSetEndCardScoreByGame.put(activeGame.gameId(), new int[] { blue, red });
			log.info("[live-activity] 세트 종료 카드 matchId={} set={} score={}:{}",
					activeGame.matchId(), setNumber, blue, red);
			applicationTaskExecutor.execute(() -> liveActivityPushService.notifySetEnd(
					activeGame.matchId(), setNumber, blue, red, false, null));
		} catch (Exception e) {
			log.warn("[live-activity] 세트 종료 카드 갱신 실패 matchId={}: {}",
					activeGame.matchId(), e.getMessage());
		}
	}

	/**
	 * 다전제 승리 조건 도달 여부. bestOf 를 모르면 매치 종료로 단정하지 않는다 —
	 * 잘못 종료로 보내면 카드가 경기 도중에 내려간다.
	 */
	static boolean isMatchEnded(Integer bestOf, Integer blueScore, Integer redScore) {
		if (bestOf == null || bestOf < 1) {
			return false;
		}
		int blue = blueScore == null ? 0 : blueScore;
		int red = redScore == null ? 0 : redScore;
		return Math.max(blue, red) >= bestOf / 2 + 1;
	}

	/**
	 * 매치 스코어 합이 세트 번호에 도달했으면 그 세트는 실제로 끝난 것이다.
	 * 스코어는 네이버 종료 확정 sync 로만 올라가므로 퍼즈 중에는 절대 도달하지 않는다.
	 */
	private boolean scoreConfirmsSetEnd(ActiveLiveGame activeGame) {
		Integer setNumber = activeGame.setNumber();
		if (setNumber == null || setNumber <= 0 || activeGame.matchId() == null) {
			return false;
		}
		try {
			return leagueMatchRepository.findById(activeGame.matchId())
					.map(match -> {
						int blue = match.getBlueScore() == null ? 0 : match.getBlueScore();
						int red = match.getRedScore() == null ? 0 : match.getRedScore();
						return blue + red >= setNumber;
					})
					.orElse(false);
		} catch (Exception e) {
			log.warn("Set-end score check failed matchId={}: {}", activeGame.matchId(), e.getMessage());
			return false;
		}
	}

	/**
	 * 이 매치의 추적 중 게임이 하나 이상 있고 전부 프레임 finished 로 확정됐는지.
	 * 하나라도 안 끝났으면(=다음 세트 진행 중) false 라 세트 사이에만 참이 된다.
	 */
	private boolean allTrackedGamesFinished(Map<String, ActiveLiveGame> activeGames, String matchId) {
		if (matchId == null || matchId.isBlank()) {
			return false;
		}
		boolean anyTracked = false;
		for (ActiveLiveGame activeGame : activeGames.values()) {
			if (!matchId.equals(activeGame.matchId())) {
				continue;
			}
			anyTracked = true;
			if (!frameFinishedGameIds.contains(activeGame.gameId())) {
				return false;
			}
		}
		return anyTracked;
	}

	/** 이 매치의 추적 중 게임 가운데 프레임이 정지한 것이 있는지. */
	private boolean hasStalledTrackedGame(Map<String, ActiveLiveGame> activeGames, String matchId) {
		if (matchId == null || matchId.isBlank()) {
			return false;
		}
		for (ActiveLiveGame activeGame : activeGames.values()) {
			if (matchId.equals(activeGame.matchId()) && frameStallTracker.isStalled(activeGame.gameId())) {
				return true;
			}
		}
		return false;
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
				//
				// 2차 판정: 프레임 정지 + 매치 스코어 확인. 업스트림이 finished 를 안 주고 피드를
				// 그냥 얼려버리는 경우가 있다(2026-07-30 HLE vs DK 2세트: 29분 동결). 정지 단독으로는
				// 절대 종료로 보지 않는다 — 퍼즈 중에도 피드가 얼 수 있고 그때 쏘면 가짜 SET_END 가
				// 나가고 dedup 이 소진돼 진짜 종료가 무음 스킵된다. 스코어 합(네이버 sync, 세트가
				// 실제로 끝나야만 갱신됨)이 세트 번호에 도달했을 때만 확정한다.
				boolean frameStalled = frameStallTracker.observeAndCheckStalled(activeGame.gameId(), window);
				if (isFrameFinished(window) || (frameStalled && scoreConfirmsSetEnd(activeGame))) {
					// store 에 마킹해야 세트 상태(LIVE/ENDED)가 stale 3분 잔상 동안 LIVE 로 남지 않는다.
					// 푸시 게이트(isEnabled/리그)와 무관하게 마킹한다.
					liveStateStore.markFinished(activeGame.gameId());
					if (frameFinishedGameIds.add(activeGame.gameId())
							&& teamLiveEventPushService.isEnabled()
							&& isNotifiableLeague(activeGame.leagueName())
							&& setEndNotifiedGameIds.add(activeGame.gameId())) {
						log.info("[live-notify] set-end({}) gameId={} matchId={} set={}",
								isFrameFinished(window) ? "frame" : "stall+score",
								activeGame.gameId(), activeGame.matchId(), activeGame.setNumber());
						fireSetEndNotification(activeGame);
					}
				}

				// 종료된 세트는 stale 제거(기본 3분) 전까지 매 tick 여기를 지난다. 그동안 스코어가
				// 늦게 도착하면 매치 종료 카드를 복구한다.
				if (frameFinishedGameIds.contains(activeGame.gameId())) {
					refreshEndedSetCard(activeGame);
				}

				liveFrameProcessor.process(activeGame, window, details);
			} catch (Exception e) {
				ActiveLiveGame failed = activeGame.increaseFailures();
				if (failed.consecutiveFailures() >= maxConsecutiveFailures) {
					log.warn("Removing game {} after {} consecutive polling failures", failed.gameId(), failed.consecutiveFailures());
					liveStateStore.removeGame(failed.gameId());
					liveObjectEventRecorder.evict(failed.gameId());
					frameStallTracker.evict(failed.gameId());
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
