package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveObjectEventRecorder {

	private static final String EVENT_DRAGON = "DRAGON";
	private static final String EVENT_BARON = "BARON";
	private static final String EVENT_TOWER = "TOWER";
	private static final String EVENT_INHIBITOR = "INHIBITOR";

	private final LiveGameObjectEventRepository objectEventRepository;
	private final Map<String, ObservedObjectState> lastObservedByGame = new ConcurrentHashMap<>();

	public void evict(String gameId) {
		if (gameId == null || gameId.isBlank()) {
			return;
		}
		lastObservedByGame.remove(gameId);
	}

	@Transactional
	public void record(ActiveLiveGame activeGame, JsonNode windowResponse) {
		JsonNode frames = windowResponse.path("frames");
		if (!frames.isArray() || frames.isEmpty()) {
			return;
		}

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
				recordTeamDiff(activeGame, "Blue", previous.blue(), snapshot.blue(), snapshot.frameTimestampUtc());
				recordTeamDiff(activeGame, "Red", previous.red(), snapshot.red(), snapshot.frameTimestampUtc());
			}

			previous = new ObservedObjectState(snapshot.frameTimestampUtc(), snapshot.blue(), snapshot.red());
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
					TeamObjectState.from(frame.path("redTeam"))));
		}
		snapshots.sort(Comparator.comparing(FrameSnapshot::frameTimestampUtc));
		return snapshots;
	}

	private void recordTeamDiff(
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
		objectEventRepository.save(event);
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

	private record TeamObjectState(int towers, int barons, int inhibitors, List<String> dragons) {

		static TeamObjectState from(JsonNode teamNode) {
			int towers = teamNode.path("towers").isNumber() ? teamNode.path("towers").asInt() : 0;
			int barons = teamNode.path("barons").isNumber() ? teamNode.path("barons").asInt() : 0;
			int inhibitors = teamNode.path("inhibitors").isNumber() ? teamNode.path("inhibitors").asInt() : 0;

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
			return new TeamObjectState(towers, barons, inhibitors, dragons);
		}
	}

	private record FrameSnapshot(LocalDateTime frameTimestampUtc, TeamObjectState blue, TeamObjectState red) {
	}

	private record ObservedObjectState(LocalDateTime frameTimestampUtc, TeamObjectState blue, TeamObjectState red) {
	}
}
