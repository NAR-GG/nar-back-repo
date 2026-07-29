package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberTeamEventPushDeliveryRepository;
import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 라이브 경기 팀 이벤트 FCM 푸시 서비스 (#21).
 * {@code PlayerSoloRankPushService} 패턴을 복제했다.
 *
 * <p>모든 동작은 {@code live.notification.fcm.enabled} 플래그로 게이트된다(기본 false).
 * 플래그가 꺼져 있으면 어떤 조회/발송도 하지 않는다.</p>
 *
 * <p>멱등 키가 팀이 아니라 matchId 라서, 한 회원이 한 경기의 양 팀을 모두 구독했더라도
 * 이벤트당 1번만 발송된다(두 번째 팀 fan-out 시 reserve()=0 → skip).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamLiveEventPushService {

	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";

	/** 모바일과 일치해야 하는 data.type 값. */
	public static final String TYPE_SET_START = "SET_START";
	public static final String TYPE_SET_END = "SET_END";
	public static final String TYPE_LIVE_EVENT = "LIVE_EVENT";

	/** SET_START / SET_END 는 세트 내 순번이 없으므로 멱등 키 event_order 에 상수 0 을 쓴다. */
	private static final long NO_EVENT_ORDER = 0L;

	private final MemberDeviceRepository deviceRepository;
	private final MemberTeamEventPushDeliveryRepository deliveryRepository;
	private final TeamExternalIdentityRepository teamExternalIdentityRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final MobilePushGateway pushGateway;
	private final MemberNotificationService notificationService;
	private final com.toy.nar.app.lolesports.WorldsService worldsService;
	private final com.toy.nar.app.lolesports.NaverEsportsScoreClient naverEsportsScoreClient;

	@Value("${live.notification.fcm.enabled:false}")
	private boolean fcmNotificationEnabled;

	/**
	 * SET_END 스코어가 방금 끝난 세트를 반영할 때까지의 업스트림 재조회 횟수/간격.
	 * 네이버는 세트 종료 후 ~1분 내 반영(실측)이라 10초 × 6회면 거의 항상 잡는다.
	 */
	@Value("${live.notification.set-end-score.retry-attempts:6}")
	private int scoreRetryAttempts;

	@Value("${live.notification.set-end-score.retry-delay-ms:10000}")
	private long scoreRetryDelayMs;

	public boolean isEnabled() {
		return fcmNotificationEnabled;
	}

	/**
	 * 세트 시작/종료 등 경기 단위 이벤트. 경기의 양 진영 팀 구독자에게 모두 발송한다(dedup 으로 1인 1회 보장).
	 */
	public void notifyMatchEvent(
			String eventType,
			String matchId,
			int setNumber,
			String blueEsportsTeamId,
			String redEsportsTeamId,
			String blueTeamName,
			String redTeamName) {
		if (!isReady() || matchId == null || matchId.isBlank()) {
			return;
		}
		// SET_END 는 매치 스코어를 함께 보여준다. 세트 N 종료 시점의 스코어 합은 반드시 N —
		// DB 가 아직 이번 세트를 반영 못 했으면(업스트림 지연·EWC unstarted 방치) 업스트림을
		// 직접 재조회하고, 그래도 stale 이면 틀린 스코어 대신 생략한다.
		String matchScoreLine = TYPE_SET_END.equals(eventType) ? buildMatchScoreLine(matchId, setNumber) : null;
		notifyTeamSide(eventType, matchId, setNumber, NO_EVENT_ORDER,
				blueEsportsTeamId, blueTeamName, redTeamName, true, matchScoreLine);
		notifyTeamSide(eventType, matchId, setNumber, NO_EVENT_ORDER,
				redEsportsTeamId, redTeamName, blueTeamName, false, matchScoreLine);

		// 경기 예약 구독자(팀 무관)에게도 발송. 매치 중립 문구를 1회 만들어 fan-out 한다.
		// dedup 키가 (member, matchId, ...) 라 팀+매치 양쪽 구독자여도 1회만 나간다.
		MobilePushMessage matchMessage =
				buildMatchScopedMessage(eventType, matchId, setNumber, blueTeamName, redTeamName, matchScoreLine);
		fanOutToMatchSubscribers(eventType, matchId, setNumber, NO_EVENT_ORDER, matchMessage);
	}

	/** 경기 예약 구독자용 중립 문구. 특정 팀 관점이 아니라 대진 기준으로 표기한다. */
	private MobilePushMessage buildMatchScopedMessage(
			String eventType,
			String matchId,
			int setNumber,
			String blueTeamName,
			String redTeamName,
			String matchScoreLine) {
		String matchup = matchup(blueTeamName, redTeamName);
		String title;
		String body;
		if (TYPE_SET_END.equals(eventType)) {
			title = matchup + " " + setNumber + "세트 종료";
			body = (matchScoreLine != null ? matchScoreLine : matchup) + " · " + setNumber + "세트 종료";
		} else {
			title = matchup + " " + setNumber + "세트 시작";
			body = matchup + " · " + setNumber + "세트 시작";
		}
		return new MobilePushMessage(title, body, baseData(eventType, matchId, setNumber));
	}

	/** 경기 예약 구독자 fan-out. 팀 구독 fanOut 과 동일하게 sendToMember(dedup) 를 태운다. */
	private void fanOutToMatchSubscribers(
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			MobilePushMessage message) {
		try {
			fanOutBatched(deviceRepository.findActiveDevicesBySubscribedMatchId(matchId, eventType),
					eventType, matchId, setNumber, eventOrder, message);
		} catch (Exception e) {
			log.warn("Failed to prepare match-subscription pushes eventType={} matchId={} setNumber={}",
					eventType, matchId, setNumber, e);
		}
	}

	/**
	 * 발송 직전 매치 스코어로 "T1 1 vs 2 HLE" 라인을 만든다.
	 *
	 * <p>세트 {@code endedSetNumber} 종료 푸시라면 스코어 합이 정확히 그 세트 수여야 한다.
	 * DB(60초 디스커버리 sync)가 방금 끝난 세트를 아직 반영 못 했으면 getEventDetails 를
	 * 짧게 재시도하며 직접 조회하고, 그래도 합이 모자라면 stale 스코어를 보여주는 대신 null 을
	 * 돌려 스코어 없이 발송한다. 세트 번호를 모르면(0 이하) 기존처럼 합>0 인 DB 값을 쓴다.</p>
	 */
	String buildMatchScoreLine(String matchId, int endedSetNumber) {
		try {
			var match = leagueMatchRepository.findById(matchId).orElse(null);
			if (match == null) {
				return null;
			}
			Integer blueScore = match.getBlueScore();
			Integer redScore = match.getRedScore();
			int dbSum = (blueScore == null ? 0 : blueScore) + (redScore == null ? 0 : redScore);

			if (endedSetNumber <= 0) {
				return dbSum > 0 ? scoreLine(match.getBlueTeamName(), blueScore, redScore, match.getRedTeamName()) : null;
			}
			String dbLine = lineIfFresh(new int[] { blueScore == null ? 0 : blueScore, redScore == null ? 0 : redScore },
					match, endedSetNumber);
			if (dbLine != null) {
				return dbLine;
			}

			// DB stale — 업스트림이 방금 끝난 세트를 반영할 때까지 짧게 재시도.
			// 네이버가 Riot gameWins 보다 항상 빨라(실측 46초~5분+ 선행) 시도마다 네이버 먼저 본다.
			// 단 네이버가 null(미커버 리그·매칭 실패·장애)이면 이후 시도에선 건너뛴다 —
			// 그날 목록에 없는 매치는 10초 뒤에도 없고, 시도마다 3초 타임아웃만 쌓인다.
			boolean naverUsable = true;
			for (int attempt = 0; attempt < scoreRetryAttempts; attempt++) {
				if (attempt > 0 && scoreRetryDelayMs > 0) {
					Thread.sleep(scoreRetryDelayMs);
				}
				if (naverUsable) {
					int[] naver = naverEsportsScoreClient.fetchScore(
							match.getBlueTeamCode(), match.getRedTeamCode(), match.getMatchDate());
					naverUsable = naver != null;
					String line = lineIfFresh(naver, match, endedSetNumber);
					if (line != null) {
						return line;
					}
				}
				String line = lineIfFresh(worldsService.fetchMatchGameWins(matchId), match, endedSetNumber);
				if (line != null) {
					return line;
				}
			}
			log.warn("Set-end score still stale after retries. matchId={} endedSet={} dbScore={}:{}",
					matchId, endedSetNumber, blueScore, redScore);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			log.warn("Failed to load match score for set-end push matchId={}", matchId, e);
			return null;
		}
	}

	/** 스코어 합이 방금 끝난 세트 수 이상(=신선)일 때만 스코어 라인, 아니면 null. */
	private String lineIfFresh(int[] score, com.toy.nar.app.lolesports.repository.LeagueMatch match, int endedSetNumber) {
		if (score == null || score[0] + score[1] < endedSetNumber) {
			return null;
		}
		return scoreLine(match.getBlueTeamName(), score[0], score[1], match.getRedTeamName());
	}

	private String scoreLine(String blueTeamName, Integer blueScore, Integer redScore, String redTeamName) {
		return matchup(blueTeamName, redTeamName)
				.replace(" vs ", " " + blueScore + " vs " + redScore + " ");
	}

	/**
	 * 라이브 이벤트(킬/오브젝트 등). 이벤트를 일으킨 팀(actingEsportsTeamId) 구독자에게 발송한다.
	 * event_order 는 같은 세트 안의 이벤트 순번이라 멱등 키에 포함된다.
	 */
	public void notifyLiveEvent(
			String matchId,
			int setNumber,
			long eventOrder,
			String actingEsportsTeamId,
			String title,
			String body) {
		if (!isReady() || matchId == null || matchId.isBlank()) {
			return;
		}
		// 문구(title/body)는 이벤트 상세(킬러/피해자·양팀 카운트)를 아는 LiveObjectEventRecorder 가 완성한다.
		// 여기서는 구독 매칭용 발송만 담당한다.
		MobilePushMessage message = new MobilePushMessage(
				title, body, baseData(TYPE_LIVE_EVENT, matchId, setNumber));
		// 팀 구독자: acting 팀이 LCK 로 해석될 때만. (비LCK 팀은 팀 구독 대상이 아니다)
		resolveLckTeam(actingEsportsTeamId).ifPresent(team ->
				fanOut(TYPE_LIVE_EVENT, matchId, setNumber, eventOrder, team.getId(), message));
		// 경기 예약 구독자: 팀 무관. 비LCK 대진(예: 국제전)이라도 발송된다.
		fanOutToMatchSubscribers(TYPE_LIVE_EVENT, matchId, setNumber, eventOrder, message);
	}

	private void notifyTeamSide(
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			String esportsTeamId,
			String teamName,
			String opponentTeamName,
			boolean blueSide,
			String matchScoreLine) {
		Optional<Team> team = resolveLckTeam(esportsTeamId);
		if (team.isEmpty()) {
			return;
		}
		Team resolved = team.get();
		String displayName = preferDisplayName(teamName, resolved.getName());
		String opponent = opponentTeamName;
		String blue = blueSide ? displayName : opponent;
		String red = blueSide ? opponent : displayName;
		MobilePushMessage message = buildMatchEventMessage(
				eventType, matchId, setNumber, displayName, blue, red, matchScoreLine);
		fanOut(eventType, matchId, setNumber, eventOrder, resolved.getId(), message);
	}

	private void fanOut(
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			Long teamId,
			MobilePushMessage message) {
		try {
			fanOutBatched(deviceRepository.findActiveDevicesBySubscribedTeamId(teamId, eventType),
					eventType, matchId, setNumber, eventOrder, message);
		} catch (Exception e) {
			log.warn("Failed to prepare team live event pushes eventType={} matchId={} setNumber={} teamId={}",
					eventType, matchId, setNumber, teamId, e);
		}
	}

	/**
	 * 구독자 전원에게 한 번의 발송 호출로 보낸다.
	 *
	 * <p>예전엔 구독자마다 {@code pushGateway.send} 를 호출해 FCM 왕복이 구독자 수만큼 났다.
	 * 실측 2026-07-29 LCK T1 vs KT 에서 구독자 약 1,500명 팬아웃이 이벤트당 8~18분 걸려
	 * (1명당 0.3~0.7초) 마지막 구독자는 세트가 끝난 뒤에 세트 시작 알림을 받았다. 게다가 세트 시작
	 * 팬아웃은 폴링 스레드에서 돌아 그 시간 동안 라이브 관측까지 멈췄다.</p>
	 *
	 * <p>게이트웨이가 500토큰씩 멀티캐스트하므로 토큰을 모아 한 번에 넘기면 왕복이 구독자 수에서
	 * 500토큰 단위로 줄어든다(1,500명이면 3회). 발송 후 토큰별 성공 여부로 구독자를 되돌려 기록한다.</p>
	 *
	 * <p>dedup 예약과 발송 기록은 아직 구독자 단위 쿼리다 — 배치화는 후속 작업으로 남긴다.</p>
	 */
	private void fanOutBatched(
			List<MemberDevice> devices,
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			MobilePushMessage message) {
		Map<Long, List<MemberDevice>> devicesByMember = devices.stream()
				.collect(Collectors.groupingBy(
						device -> device.getMember().getId(),
						LinkedHashMap::new,
						Collectors.toList()));

		// dedup: 양 팀 구독자라도 (member, matchId, setNumber, eventType, eventOrder) 1회만 통과한다.
		Map<Long, List<String>> tokensByMember = new LinkedHashMap<>();
		for (Map.Entry<Long, List<MemberDevice>> entry : devicesByMember.entrySet()) {
			if (!deliveryRepository.reserve(entry.getKey(), matchId, setNumber, eventType, eventOrder)) {
				continue;
			}
			tokensByMember.put(entry.getKey(),
					entry.getValue().stream().map(MemberDevice::getFcmToken).toList());
		}
		if (tokensByMember.isEmpty()) {
			return;
		}

		List<String> allTokens = tokensByMember.values().stream().flatMap(List::stream).toList();
		MobilePushResult result;
		try {
			result = pushGateway.send(allTokens, message);
		} catch (Exception e) {
			// 발송 자체가 실패하면 예약한 구독자 전원을 FAILED 로 남긴다(재예약 대상이 된다).
			tokensByMember.keySet().forEach(memberId ->
					markFailed(memberId, matchId, setNumber, eventType, eventOrder, truncate(e.getMessage())));
			log.warn("Team live event multicast failed eventType={} matchId={} setNumber={} members={} tokens={}",
					eventType, matchId, setNumber, tokensByMember.size(), allTokens.size(), e);
			return;
		}

		Set<String> successTokens = new HashSet<>(result.successTokens());
		for (Map.Entry<Long, List<String>> entry : tokensByMember.entrySet()) {
			Long memberId = entry.getKey();
			boolean delivered = entry.getValue().stream().anyMatch(successTokens::contains);
			try {
				if (delivered) {
					deliveryRepository.markSent(memberId, matchId, setNumber, eventType, eventOrder);
					recordFeed(memberId, eventType, message);
				} else {
					deliveryRepository.markFailed(memberId, matchId, setNumber, eventType, eventOrder,
							"FCM 전송 성공 기기가 없습니다.");
				}
			} catch (Exception e) {
				markFailed(memberId, matchId, setNumber, eventType, eventOrder, truncate(e.getMessage()));
				log.warn("Team live event push record failed memberId={} eventType={} matchId={} setNumber={} eventOrder={}",
						memberId, eventType, matchId, setNumber, eventOrder, e);
			}
		}

		if (!result.invalidTokens().isEmpty()) {
			deactivateInvalidTokens(result.invalidTokens(), matchId, eventType);
		}
	}

	private void deactivateInvalidTokens(List<String> invalidTokens, String matchId, String eventType) {
		try {
			deviceRepository.deactivateByFcmTokenIn(invalidTokens);
		} catch (Exception e) {
			log.warn("Failed to deactivate invalid FCM tokens matchId={} eventType={}", matchId, eventType, e);
		}
	}

	private void markFailed(
			Long memberId,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder,
			String errorMessage) {
		try {
			deliveryRepository.markFailed(memberId, matchId, setNumber, eventType, eventOrder, errorMessage);
		} catch (Exception persistenceException) {
			log.warn("Failed to persist team push failure memberId={} matchId={} eventType={}",
					memberId, matchId, eventType, persistenceException);
		}
	}

	/** esportsTeamId → Team 매핑 후 LCK 팀만 통과시킨다. 세트마다 진영이 스왑되므로 매 호출마다 진영별로 해석한다. */
	private Optional<Team> resolveLckTeam(String esportsTeamId) {
		if (esportsTeamId == null || esportsTeamId.isBlank()) {
			return Optional.empty();
		}
		return teamExternalIdentityRepository
				.findBySourceAndExternalTeamId(LOLESPORTS_SOURCE, esportsTeamId)
				.map(identity -> identity.getTeam())
				.filter(team -> LckTeamCatalog.contains(team.getCode()));
	}

	/** 마이구독 알림 피드에 기록한다. 피드 실패가 푸시 흐름을 깨면 안 되므로 예외를 흡수한다. */
	private void recordFeed(Long memberId, String eventType, MobilePushMessage message) {
		try {
			notificationService.record(
					memberId,
					MemberNotificationType.valueOf(eventType),
					message.title(),
					message.body(),
					message.data());
		} catch (Exception e) {
			log.warn("Failed to record team event notification feed memberId={} eventType={}",
					memberId, eventType, e);
		}
	}

	private MobilePushMessage buildMatchEventMessage(
			String eventType,
			String matchId,
			int setNumber,
			String teamName,
			String blueTeamName,
			String redTeamName,
			String matchScoreLine) {
		String matchup = matchup(blueTeamName, redTeamName);
		String title;
		String body;
		if (TYPE_SET_END.equals(eventType)) {
			title = teamName + " " + setNumber + "세트 종료";
			// 매치 스코어를 알면 "T1 1 vs 2 HLE" 로 경기 흐름을 보여준다.
			body = matchScoreLine != null
					? matchScoreLine + " · " + setNumber + "세트 종료"
					: matchup + " · " + setNumber + "세트 종료";
		} else {
			title = teamName + " 세트 시작";
			body = matchup + " · " + setNumber + "세트 시작";
		}
		return new MobilePushMessage(title, body, baseData(eventType, matchId, setNumber));
	}

	/** 모바일 계약: data.type / data.matchId / data.setNumber 는 모두 String. */
	private Map<String, String> baseData(String eventType, String matchId, int setNumber) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", eventType);
		data.put("matchId", matchId);
		data.put("setNumber", String.valueOf(setNumber));
		return Map.copyOf(data);
	}

	private String matchup(String blueTeamName, String redTeamName) {
		String blue = blueTeamName == null || blueTeamName.isBlank() ? "Blue" : blueTeamName;
		String red = redTeamName == null || redTeamName.isBlank() ? "Red" : redTeamName;
		return blue + " vs " + red;
	}

	private String preferDisplayName(String feedName, String resolvedName) {
		if (feedName != null && !feedName.isBlank()) {
			return feedName;
		}
		return resolvedName;
	}

	private boolean isReady() {
		return fcmNotificationEnabled && pushGateway.isAvailable();
	}

	private String truncate(String message) {
		String normalized = message == null || message.isBlank() ? "알 수 없는 FCM 오류" : message;
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}
}
