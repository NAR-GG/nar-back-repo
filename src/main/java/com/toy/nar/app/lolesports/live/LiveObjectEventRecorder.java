package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveObjectEventRecorder {

	private static final String EVENT_DRAGON = "DRAGON";
	private static final String EVENT_BARON = "BARON";
	private static final String EVENT_TOWER = "TOWER";
	private static final String EVENT_INHIBITOR = "INHIBITOR";
	private static final String EVENT_KILL = "KILL";

	private final LiveGameObjectEventRepository objectEventRepository;
	private final NotificationService notificationService;
	private final com.toy.nar.app.mobile.push.TeamLiveEventPushService teamLiveEventPushService;
	private final Map<String, ObservedObjectState> lastObservedByGame = new ConcurrentHashMap<>();
	// [FCM #21] 세트마다 진영이 스왑되므로, 피드 window 의 진영별 esportsTeamId 를 게임별로 기억한다(Blue/Red → esportsTeamId).
	private final Map<String, SideTeamIds> sideTeamIdsByGame = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Value("${lolesports.live.notification.events-enabled:false}")
	private boolean eventNotificationEnabled;

	// 디스코드 알림을 보낼 리그(쉼표 구분). 기본 LCK 만. 그 외 리그는 이벤트를 DB에 기록만 하고 알림은 안 보낸다.
	@org.springframework.beans.factory.annotation.Value("${lolesports.live.notification.leagues:LCK}")
	private String notificationLeagues;

	public void evict(String gameId) {
		if (gameId == null || gameId.isBlank()) {
			return;
		}
		lastObservedByGame.remove(gameId);
		sideTeamIdsByGame.remove(gameId);
	}

	@Transactional
	public void record(ActiveLiveGame activeGame, JsonNode windowResponse) {
		JsonNode frames = windowResponse.path("frames");
		if (!frames.isArray() || frames.isEmpty()) {
			return;
		}

		JsonNode gameMetadata = windowResponse.path("gameMetadata");
		Map<Integer, ParticipantMeta> metadata = parseMetadata(gameMetadata);
		// [FCM #21] 진영별 esportsTeamId 기억(세트마다 스왑되므로 window 기준이 신뢰 가능). 플래그 무관하게 가벼운 파싱.
		rememberSideTeamIds(activeGame.gameId(), gameMetadata);

		List<FrameSnapshot> snapshots = toSnapshots(frames);
		if (snapshots.isEmpty()) {
			return;
		}

		ObservedObjectState previous = lastObservedByGame.get(activeGame.gameId());
		for (FrameSnapshot snapshot : snapshots) {
			if (previous != null && !snapshot.frameTimestampUtc().isAfter(previous.frameTimestampUtc())) {
				continue;
			}
			if (previous != null) {
				recordObjectDiff(activeGame, "Blue", previous.blue(), snapshot.blue(), snapshot.frameTimestampUtc());
				recordObjectDiff(activeGame, "Red", previous.red(), snapshot.red(), snapshot.frameTimestampUtc());
				recordKills(activeGame, "Blue", previous.blue().kills(), snapshot.blue().kills(),
						previous.blueParticipants(), snapshot.blueParticipants(),
						previous.redParticipants(), snapshot.redParticipants(),
						metadata, snapshot.frameTimestampUtc());
				recordKills(activeGame, "Red", previous.red().kills(), snapshot.red().kills(),
						previous.redParticipants(), snapshot.redParticipants(),
						previous.blueParticipants(), snapshot.blueParticipants(),
						metadata, snapshot.frameTimestampUtc());
			}

			previous = new ObservedObjectState(snapshot.frameTimestampUtc(), snapshot.blue(), snapshot.red(),
					snapshot.blueParticipants(), snapshot.redParticipants());
		}

		lastObservedByGame.put(activeGame.gameId(), previous);
	}

	private List<FrameSnapshot> toSnapshots(JsonNode frames) {
		List<FrameSnapshot> snapshots = new ArrayList<>();
		for (JsonNode frame : frames) {
			LocalDateTime frameTimestampUtc = parseFrameTimestamp(frame.path("rfc460Timestamp").asText(null));
			if (frameTimestampUtc == null) {
				continue;
			}
			snapshots.add(new FrameSnapshot(
					frameTimestampUtc,
					TeamObjectState.from(frame.path("blueTeam")),
					TeamObjectState.from(frame.path("redTeam")),
					parseParticipants(frame.path("blueTeam").path("participants")),
					parseParticipants(frame.path("redTeam").path("participants"))));
		}
		snapshots.sort(Comparator.comparing(FrameSnapshot::frameTimestampUtc));
		return snapshots;
	}

	private void recordObjectDiff(
			ActiveLiveGame activeGame,
			String teamSide,
			TeamObjectState previous,
			TeamObjectState current,
			LocalDateTime frameTimestampUtc) {
		recordCounterEvents(activeGame, teamSide, EVENT_TOWER, previous.towers(), current.towers(), frameTimestampUtc);
		recordCounterEvents(activeGame, teamSide, EVENT_BARON, previous.barons(), current.barons(), frameTimestampUtc);
		recordCounterEvents(activeGame, teamSide, EVENT_INHIBITOR, previous.inhibitors(), current.inhibitors(),
				frameTimestampUtc);
		recordDragonEvents(activeGame, teamSide, previous.dragons(), current.dragons(), frameTimestampUtc);
	}

	/**
	 * 킬 이벤트 감지. 팀 totalKills 증가분을 순서(event_order)로 쓰고,
	 * 같은 프레임 안에서 kills가 증가한 참가자(킬러)와 상대팀 deaths가 증가한 참가자(피해자)를 그리디 페어링한다.
	 * 한타로 여러 명이 동시에 죽은 프레임은 1:1 매칭이 근사적이다.
	 */
	private void recordKills(
			ActiveLiveGame activeGame,
			String killerSide,
			int previousTeamKills,
			int currentTeamKills,
			Map<Integer, Kd> killerPrevKd,
			Map<Integer, Kd> killerCurKd,
			Map<Integer, Kd> victimPrevKd,
			Map<Integer, Kd> victimCurKd,
			Map<Integer, ParticipantMeta> metadata,
			LocalDateTime frameTimestampUtc) {
		if (currentTeamKills <= previousTeamKills) {
			return;
		}

		List<Integer> killers = expandByDelta(killerPrevKd, killerCurKd, true);
		List<Integer> victims = expandByDelta(victimPrevKd, victimCurKd, false);

		int newKills = currentTeamKills - previousTeamKills;
		for (int i = 0; i < newKills; i++) {
			int order = previousTeamKills + 1 + i;
			Integer killerId = i < killers.size() ? killers.get(i) : null;
			Integer victimId = i < victims.size() ? victims.get(i) : null;
			saveKillEventIfAbsent(activeGame, killerSide, order, currentTeamKills, killerId, victimId, metadata,
					frameTimestampUtc);
		}
	}

	private void recordCounterEvents(
			ActiveLiveGame activeGame,
			String teamSide,
			String eventType,
			int previousValue,
			int currentValue,
			LocalDateTime frameTimestampUtc) {
		if (currentValue <= previousValue) {
			return;
		}

		for (int order = previousValue + 1; order <= currentValue; order++) {
			saveEventIfAbsent(
					activeGame,
					teamSide,
					eventType,
					null,
					order,
					order,
					frameTimestampUtc);
		}
	}

	private void recordDragonEvents(
			ActiveLiveGame activeGame,
			String teamSide,
			List<String> previousDragons,
			List<String> currentDragons,
			LocalDateTime frameTimestampUtc) {
		if (currentDragons.size() <= previousDragons.size()) {
			return;
		}

		for (int index = previousDragons.size(); index < currentDragons.size(); index++) {
			String dragonType = currentDragons.get(index);
			saveEventIfAbsent(
					activeGame,
					teamSide,
					EVENT_DRAGON,
					dragonType,
					index + 1,
					index + 1,
					frameTimestampUtc);
		}
	}

	private void saveEventIfAbsent(
			ActiveLiveGame activeGame,
			String teamSide,
			String eventType,
			String eventSubType,
			int eventOrder,
			int valueAfter,
			LocalDateTime frameTimestampUtc) {
		boolean exists = objectEventRepository.existsByGameIdAndTeamSideAndEventTypeAndEventOrder(
				activeGame.gameId(), teamSide, eventType, eventOrder);
		if (exists) {
			return;
		}

		LiveGameObjectEvent event = new LiveGameObjectEvent(
				activeGame.gameId(),
				activeGame.matchId(),
				activeGame.leagueName(),
				teamSide,
				eventType,
				eventSubType,
				eventOrder,
				valueAfter,
				frameTimestampUtc);
		event = objectEventRepository.save(event);
		log.info("[live-notify] event saved type={} team={} order={} gameId={} notify={}",
				eventType, teamSide, eventOrder, activeGame.gameId(), eventNotificationEnabled);

		if (eventNotificationEnabled && isNotifiableLeague(activeGame.leagueName())) {
			notificationService.sendLiveObjectEventNotification(
					activeGame.leagueName(),
					teamNameOf(activeGame, teamSide),
					teamSide,
					eventType,
					eventSubType,
					eventOrder,
					activeGame.gameId());
		}

		// [FCM #21] LIVE_EVENT 푸시. live.notification.fcm.enabled 플래그로 게이트.
		// 멱등 키 event_order 는 킬/오브젝트 충돌을 막기 위해 저장된 이벤트의 전역 id 를 쓴다.
		if (teamLiveEventPushService.isEnabled() && isNotifiableLeague(activeGame.leagueName())) {
			fireLiveEventPush(activeGame, teamSide, objectEventLabel(eventType, eventSubType), event.getId());
		}
	}

	private void saveKillEventIfAbsent(
			ActiveLiveGame activeGame,
			String killerSide,
			int eventOrder,
			int teamKillsAfter,
			Integer killerId,
			Integer victimId,
			Map<Integer, ParticipantMeta> metadata,
			LocalDateTime frameTimestampUtc) {
		boolean exists = objectEventRepository.existsByGameIdAndTeamSideAndEventTypeAndEventOrder(
				activeGame.gameId(), killerSide, EVENT_KILL, eventOrder);
		if (exists) {
			return;
		}

		ParticipantMeta killer = killerId == null ? null : metadata.get(killerId);
		ParticipantMeta victim = victimId == null ? null : metadata.get(victimId);
		String killerChampion = killer == null ? null : killer.champion();

		// event_sub_type 은 하위호환을 위해 킬러 챔피언을 유지하고, 킬러/피해자 선수명과 피해자 챔피언을 신규 컬럼에 함께 저장한다.
		LiveGameObjectEvent event = new LiveGameObjectEvent(
				activeGame.gameId(),
				activeGame.matchId(),
				activeGame.leagueName(),
				killerSide,
				EVENT_KILL,
				killerChampion,
				eventOrder,
				teamKillsAfter,
				killer == null ? null : killer.summonerName(),
				victim == null ? null : victim.champion(),
				victim == null ? null : victim.summonerName(),
				frameTimestampUtc);
		event = objectEventRepository.save(event);
		log.info("[live-notify] kill saved order={} team={} killer={} victim={} gameId={} notify={}",
				eventOrder, killerSide,
				killer == null ? "?" : killer.summonerName(),
				victim == null ? "?" : victim.summonerName(),
				activeGame.gameId(), eventNotificationEnabled);

		if (eventNotificationEnabled && isNotifiableLeague(activeGame.leagueName())) {
			notificationService.sendLiveKillNotification(
					activeGame.leagueName(),
					teamNameOf(activeGame, killerSide),
					killerSide,
					killer == null ? null : killer.summonerName(),
					killerChampion,
					victim == null ? null : victim.summonerName(),
					victim == null ? null : victim.champion(),
					eventOrder,
					activeGame.gameId());
		}

		// [FCM #21] LIVE_EVENT(킬) 푸시. live.notification.fcm.enabled 플래그로 게이트.
		if (teamLiveEventPushService.isEnabled() && isNotifiableLeague(activeGame.leagueName())) {
			fireLiveEventPush(activeGame, killerSide, killEventLabel(killerSide, teamKillsAfter), event.getId());
		}
	}

	private String teamNameOf(ActiveLiveGame activeGame, String teamSide) {
		if ("Blue".equalsIgnoreCase(teamSide)) {
			return activeGame.blueTeamName();
		}
		if ("Red".equalsIgnoreCase(teamSide)) {
			return activeGame.redTeamName();
		}
		return null;
	}

	/** prev 대비 cur에서 kills(또는 deaths)가 증가한 참가자를 증가분만큼 펼친 목록(참가자 ID 오름차순). */
	private List<Integer> expandByDelta(Map<Integer, Kd> prev, Map<Integer, Kd> cur, boolean useKills) {
		List<Integer> expanded = new ArrayList<>();
		for (Map.Entry<Integer, Kd> entry : new TreeMap<>(cur).entrySet()) {
			int pid = entry.getKey();
			Kd before = prev.get(pid);
			int prevValue = before == null ? 0 : (useKills ? before.kills() : before.deaths());
			int curValue = useKills ? entry.getValue().kills() : entry.getValue().deaths();
			for (int n = 0; n < curValue - prevValue; n++) {
				expanded.add(pid);
			}
		}
		return expanded;
	}

	private Map<Integer, Kd> parseParticipants(JsonNode participantsNode) {
		Map<Integer, Kd> result = new HashMap<>();
		if (participantsNode == null || !participantsNode.isArray()) {
			return result;
		}
		for (JsonNode p : participantsNode) {
			if (!p.path("participantId").isNumber()) {
				continue;
			}
			int pid = p.path("participantId").asInt();
			int kills = p.path("kills").isNumber() ? p.path("kills").asInt() : 0;
			int deaths = p.path("deaths").isNumber() ? p.path("deaths").asInt() : 0;
			result.put(pid, new Kd(kills, deaths));
		}
		return result;
	}

	private Map<Integer, ParticipantMeta> parseMetadata(JsonNode gameMetadata) {
		Map<Integer, ParticipantMeta> result = new HashMap<>();
		if (gameMetadata == null || gameMetadata.isMissingNode()) {
			return result;
		}
		for (String teamKey : new String[] { "blueTeamMetadata", "redTeamMetadata" }) {
			JsonNode arr = gameMetadata.path(teamKey).path("participantMetadata");
			if (!arr.isArray()) {
				continue;
			}
			for (JsonNode p : arr) {
				if (!p.path("participantId").isNumber()) {
					continue;
				}
				int pid = p.path("participantId").asInt();
				String summoner = p.path("summonerName").asText(null);
				String champion = p.path("championId").asText(null);
				result.put(pid, new ParticipantMeta(summoner, champion));
			}
		}
		return result;
	}

	/**
	 * [FCM #21] 피드 window 의 진영별 esportsTeamId 를 기억한다. 세트마다 진영이 스왑되므로
	 * 매치 고정 ID 가 아니라 이 값으로 진영-팀을 맞춰야 한다.
	 */
	private void rememberSideTeamIds(String gameId, JsonNode gameMetadata) {
		if (gameId == null || gameMetadata == null || gameMetadata.isMissingNode()) {
			return;
		}
		String blue = textOrNull(gameMetadata.path("blueTeamMetadata"), "esportsTeamId");
		String red = textOrNull(gameMetadata.path("redTeamMetadata"), "esportsTeamId");
		if (blue != null || red != null) {
			sideTeamIdsByGame.put(gameId, new SideTeamIds(blue, red));
		}
	}

	/** [FCM #21] LIVE_EVENT 푸시 호출. 진영별 esportsTeamId(window 기준)로 이벤트를 일으킨 팀 구독자에게 발송. */
	private void fireLiveEventPush(ActiveLiveGame activeGame, String teamSide, String eventLabel, long eventOrder) {
		try {
			SideTeamIds sideIds = sideTeamIdsByGame.get(activeGame.gameId());
			if (sideIds == null) {
				return;
			}
			boolean blue = "Blue".equalsIgnoreCase(teamSide);
			String actingEsportsTeamId = blue ? sideIds.blue() : sideIds.red();
			if (actingEsportsTeamId == null || actingEsportsTeamId.isBlank()) {
				return;
			}
			String actingTeamName = teamNameOf(activeGame, teamSide);
			String opponentTeamName = teamNameOf(activeGame, blue ? "Red" : "Blue");
			teamLiveEventPushService.notifyLiveEvent(
					activeGame.matchId(),
					activeGame.setNumber() != null ? activeGame.setNumber() : 0,
					eventOrder,
					actingEsportsTeamId,
					actingTeamName,
					opponentTeamName,
					eventLabel);
		} catch (Exception e) {
			log.warn("[live-notify] live-event FCM failed gameId={} side={}: {}",
					activeGame.gameId(), teamSide, e.getMessage());
		}
	}

	private String objectEventLabel(String eventType, String eventSubType) {
		return switch (eventType) {
			case EVENT_DRAGON -> "드래곤 처치";
			case EVENT_BARON -> "바론 처치";
			case EVENT_TOWER -> "포탑 파괴";
			case EVENT_INHIBITOR -> "억제기 파괴";
			default -> eventType;
		};
	}

	private String killEventLabel(String killerSide, int teamKillsAfter) {
		return teamKillsAfter + "킬 달성";
	}

	private String textOrNull(JsonNode node, String field) {
		if (node == null || node.isMissingNode()) {
			return null;
		}
		JsonNode target = node.path(field);
		if (target.isMissingNode() || target.isNull()) {
			return null;
		}
		String value = target.asText(null);
		return value == null || value.isBlank() ? null : value;
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

	private LocalDateTime parseFrameTimestamp(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC);
		} catch (Exception e) {
			log.debug("Failed to parse object frame timestamp: {}", raw);
			return null;
		}
	}

	private record Kd(int kills, int deaths) {
	}

	/** [FCM #21] 피드 window 기준 진영별 esportsTeamId. 세트마다 스왑되므로 게임별로 캐시한다. */
	private record SideTeamIds(String blue, String red) {
	}

	private record ParticipantMeta(String summonerName, String champion) {
	}

	private record TeamObjectState(int towers, int barons, int inhibitors, int kills, List<String> dragons) {

		static TeamObjectState from(JsonNode teamNode) {
			int towers = teamNode.path("towers").isNumber() ? teamNode.path("towers").asInt() : 0;
			int barons = teamNode.path("barons").isNumber() ? teamNode.path("barons").asInt() : 0;
			int inhibitors = teamNode.path("inhibitors").isNumber() ? teamNode.path("inhibitors").asInt() : 0;
			int kills = teamNode.path("totalKills").isNumber() ? teamNode.path("totalKills").asInt() : 0;

			List<String> dragons = new ArrayList<>();
			JsonNode dragonNode = teamNode.path("dragons");
			if (dragonNode.isArray()) {
				for (JsonNode dragon : dragonNode) {
					String dragonType = dragon.asText(null);
					if (dragonType != null && !dragonType.isBlank()) {
						dragons.add(dragonType);
					}
				}
			}
			return new TeamObjectState(towers, barons, inhibitors, kills, dragons);
		}
	}

	private record FrameSnapshot(
			LocalDateTime frameTimestampUtc,
			TeamObjectState blue,
			TeamObjectState red,
			Map<Integer, Kd> blueParticipants,
			Map<Integer, Kd> redParticipants) {
	}

	private record ObservedObjectState(
			LocalDateTime frameTimestampUtc,
			TeamObjectState blue,
			TeamObjectState red,
			Map<Integer, Kd> blueParticipants,
			Map<Integer, Kd> redParticipants) {
	}
}
