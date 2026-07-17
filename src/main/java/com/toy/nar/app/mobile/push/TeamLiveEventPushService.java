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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	@Value("${live.notification.fcm.enabled:false}")
	private boolean fcmNotificationEnabled;

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
		// SET_END 는 매치 스코어를 함께 보여준다. 같은 폴링 사이클에서 스코어 sync 가
		// 발송보다 먼저 실행되므로 대부분 최신이지만, 합계가 0이면(미동기화) 생략한다.
		String matchScoreLine = TYPE_SET_END.equals(eventType) ? buildMatchScoreLine(matchId) : null;
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
			List<MemberDevice> devices = deviceRepository.findActiveDevicesBySubscribedMatchId(matchId, eventType);
			Map<Long, List<MemberDevice>> devicesByMember = devices.stream()
					.collect(Collectors.groupingBy(
							device -> device.getMember().getId(),
							LinkedHashMap::new,
							Collectors.toList()));
			for (Map.Entry<Long, List<MemberDevice>> entry : devicesByMember.entrySet()) {
				sendToMember(entry.getKey(), entry.getValue(), eventType, matchId, setNumber, eventOrder, message);
			}
		} catch (Exception e) {
			log.warn("Failed to prepare match-subscription pushes eventType={} matchId={} setNumber={}",
					eventType, matchId, setNumber, e);
		}
	}

	/** 발송 직전 DB 매치 스코어로 "T1 1 vs 2 HLE" 라인을 만든다. 스코어가 아직 0:0 이면 null. */
	private String buildMatchScoreLine(String matchId) {
		try {
			return leagueMatchRepository.findById(matchId)
					.map(match -> {
						Integer blueScore = match.getBlueScore();
						Integer redScore = match.getRedScore();
						if (blueScore == null || redScore == null || blueScore + redScore <= 0) {
							return null;
						}
						return matchup(match.getBlueTeamName(), match.getRedTeamName())
								.replace(" vs ", " " + blueScore + " vs " + redScore + " ");
					})
					.orElse(null);
		} catch (Exception e) {
			log.warn("Failed to load match score for set-end push matchId={}", matchId, e);
			return null;
		}
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
			List<MemberDevice> devices =
					deviceRepository.findActiveDevicesBySubscribedTeamId(teamId, eventType);
			Map<Long, List<MemberDevice>> devicesByMember = devices.stream()
					.collect(Collectors.groupingBy(
							device -> device.getMember().getId(),
							LinkedHashMap::new,
							Collectors.toList()));
			for (Map.Entry<Long, List<MemberDevice>> entry : devicesByMember.entrySet()) {
				sendToMember(entry.getKey(), entry.getValue(), eventType, matchId, setNumber, eventOrder, message);
			}
		} catch (Exception e) {
			log.warn("Failed to prepare team live event pushes eventType={} matchId={} setNumber={} teamId={}",
					eventType, matchId, setNumber, teamId, e);
		}
	}

	private void sendToMember(
			Long memberId,
			List<MemberDevice> devices,
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			MobilePushMessage message) {
		try {
			// dedup: 양 팀 구독자라도 (member, matchId, setNumber, eventType, eventOrder) 1회만 통과한다.
			if (!deliveryRepository.reserve(memberId, matchId, setNumber, eventType, eventOrder)) {
				return;
			}
			List<String> tokens = devices.stream().map(MemberDevice::getFcmToken).toList();
			MobilePushResult result = pushGateway.send(tokens, message);
			if (!result.invalidTokens().isEmpty()) {
				deactivateInvalidTokens(result.invalidTokens(), matchId, eventType);
			}
			if (result.successCount() > 0) {
				deliveryRepository.markSent(memberId, matchId, setNumber, eventType, eventOrder);
				recordFeed(memberId, eventType, message);
			} else {
				deliveryRepository.markFailed(memberId, matchId, setNumber, eventType, eventOrder,
						"FCM 전송 성공 기기가 없습니다.");
			}
		} catch (Exception e) {
			markFailed(memberId, matchId, setNumber, eventType, eventOrder, truncate(e.getMessage()));
			log.warn("Team live event push failed memberId={} eventType={} matchId={} setNumber={} eventOrder={}",
					memberId, eventType, matchId, setNumber, eventOrder, e);
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
