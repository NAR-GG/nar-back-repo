package com.toy.nar.app.mobile.push;

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
		notifyTeamSide(eventType, matchId, setNumber, NO_EVENT_ORDER,
				blueEsportsTeamId, blueTeamName, redTeamName, true);
		notifyTeamSide(eventType, matchId, setNumber, NO_EVENT_ORDER,
				redEsportsTeamId, redTeamName, blueTeamName, false);
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
			String actingTeamName,
			String opponentTeamName,
			String eventLabel) {
		if (!isReady() || matchId == null || matchId.isBlank()) {
			return;
		}
		Optional<Team> team = resolveLckTeam(actingEsportsTeamId);
		if (team.isEmpty()) {
			return;
		}
		Team resolved = team.get();
		String teamName = preferDisplayName(actingTeamName, resolved.getName());
		MobilePushMessage message = buildLiveEventMessage(
				matchId, setNumber, teamName, eventLabel);
		fanOut(TYPE_LIVE_EVENT, matchId, setNumber, eventOrder, resolved.getId(), message);
	}

	private void notifyTeamSide(
			String eventType,
			String matchId,
			int setNumber,
			long eventOrder,
			String esportsTeamId,
			String teamName,
			String opponentTeamName,
			boolean blueSide) {
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
				eventType, matchId, setNumber, displayName, blue, red);
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
			String redTeamName) {
		String matchup = matchup(blueTeamName, redTeamName);
		String title;
		String body;
		if (TYPE_SET_END.equals(eventType)) {
			title = teamName + " 세트 종료";
			body = matchup + " · " + setNumber + "세트 종료";
		} else {
			title = teamName + " 세트 시작";
			body = matchup + " · " + setNumber + "세트 시작";
		}
		return new MobilePushMessage(title, body, baseData(eventType, matchId, setNumber));
	}

	private MobilePushMessage buildLiveEventMessage(
			String matchId,
			int setNumber,
			String teamName,
			String eventLabel) {
		String label = eventLabel == null || eventLabel.isBlank() ? "라이브 이벤트" : eventLabel;
		String title = teamName + " " + label;
		String body = teamName + " " + label + " · " + setNumber + "세트";
		return new MobilePushMessage(title, body, baseData(TYPE_LIVE_EVENT, matchId, setNumber));
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
