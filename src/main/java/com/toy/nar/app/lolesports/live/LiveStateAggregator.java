package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStateAggregator {

	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";

	private final ObjectMapper objectMapper;
	private final RuneMetadataResolver runeMetadataResolver;
	private final ItemMetadataResolver itemMetadataResolver;
	private final TeamExternalIdentityRepository teamExternalIdentityRepository;

	// 세트마다 블루/레드 진영이 바뀌므로, 피드의 진영별 esportsTeamId 로 팀명을 조회한다(esportsTeamId→팀명 캐시).
	private final Map<String, String> teamNameByExternalId = new ConcurrentHashMap<>();

	public LiveGameState aggregate(
			ActiveLiveGame activeGame,
			JsonNode windowResponse,
			JsonNode detailsResponse) {
		JsonNode latestFrame = extractLatestFrame(detailsResponse);
		if (latestFrame == null || latestFrame.isMissingNode()) {
			return null;
		}

		String frameTimestampRaw = latestFrame.path("rfc460Timestamp").asText(null);
		if (frameTimestampRaw == null || frameTimestampRaw.isBlank()) {
			return null;
		}

		LocalDateTime frameTimestampUtc = LocalDateTime.ofInstant(Instant.parse(frameTimestampRaw), ZoneOffset.UTC);
		LocalDateTime minuteBucketUtc = frameTimestampUtc.withSecond(0).withNano(0);

		JsonNode gameMetadata = windowResponse.path("gameMetadata");
		JsonNode blueTeamMetadata = gameMetadata.path("blueTeamMetadata");
		JsonNode redTeamMetadata = gameMetadata.path("redTeamMetadata");

		Set<Integer> blueParticipantIds = collectParticipantIds(blueTeamMetadata.path("participantMetadata"));
		Map<Integer, JsonNode> participantMetaById = collectParticipantMeta(gameMetadata);

		List<LiveParticipantState> participants = new ArrayList<>();
		JsonNode frameParticipants = latestFrame.path("participants");
		if (frameParticipants.isArray()) {
			for (JsonNode frameParticipant : frameParticipants) {
				Integer participantId = frameParticipant.path("participantId").isMissingNode()
						? null
						: frameParticipant.path("participantId").asInt();
				if (participantId == null) {
					continue;
				}

				JsonNode participantMeta = participantMetaById.get(participantId);
				JsonNode perkMetadata = frameParticipant.path("perkMetadata");
				String perksJson = serializeJson(perkMetadata);
				Integer primaryStyleId = intOrNull(perkMetadata, "styleId");
				Integer subStyleId = intOrNull(perkMetadata, "subStyleId");
				List<Integer> runeIds = intList(perkMetadata.path("perks"));
				String primaryStyleName = runeMetadataResolver.resolveStyleName(primaryStyleId);
				String subStyleName = runeMetadataResolver.resolveStyleName(subStyleId);
				List<String> runeNames = runeMetadataResolver.resolveRuneNames(runeIds);
				List<Integer> itemIds = intList(frameParticipant.path("items"));
				List<String> itemNames = itemMetadataResolver.resolveItemNames(itemIds);
				List<String> itemImageUrls = itemMetadataResolver.resolveItemImageUrls(itemIds);

				participants.add(new LiveParticipantState(
						participantId,
						blueParticipantIds.contains(participantId) ? "Blue" : "Red",
						textOrNull(participantMeta, "role"),
						textOrNull(participantMeta, "summonerName"),
						textOrNull(participantMeta, "esportsPlayerId"),
						textOrNull(participantMeta, "championId"),
						intOrNull(frameParticipant, "level"),
						intOrNull(frameParticipant, "kills"),
						intOrNull(frameParticipant, "deaths"),
						intOrNull(frameParticipant, "assists"),
						intOrNull(frameParticipant, "totalGoldEarned"),
						intOrNull(frameParticipant, "creepScore"),
						doubleOrNull(frameParticipant, "killParticipation"),
						doubleOrNull(frameParticipant, "championDamageShare"),
						itemIds,
						itemNames,
						itemImageUrls,
						perksJson,
						primaryStyleName,
						subStyleName,
						runeNames,
						intOrNull(frameParticipant, "wardsPlaced"),
						intOrNull(frameParticipant, "wardsDestroyed")));
			}
		}

		return new LiveGameState(
				activeGame.gameId(),
				activeGame.matchId(),
				activeGame.leagueName(),
				resolveTeamName(textOrNull(blueTeamMetadata, "esportsTeamId"), activeGame.blueTeamName()),
				resolveTeamName(textOrNull(redTeamMetadata, "esportsTeamId"), activeGame.redTeamName()),
				minuteBucketUtc,
				frameTimestampUtc,
				participants,
				List.of());
	}

	private JsonNode extractLatestFrame(JsonNode detailsResponse) {
		JsonNode frames = detailsResponse.path("frames");
		if (!frames.isArray() || frames.isEmpty()) {
			return null;
		}

		Instant latest = Instant.EPOCH;
		JsonNode latestFrame = null;

		for (JsonNode frame : frames) {
			String rawTimestamp = frame.path("rfc460Timestamp").asText(null);
			if (rawTimestamp == null || rawTimestamp.isBlank()) {
				continue;
			}
			try {
				Instant timestamp = Instant.parse(rawTimestamp);
				if (timestamp.isAfter(latest)) {
					latest = timestamp;
					latestFrame = frame;
				}
			} catch (Exception e) {
				log.debug("Failed to parse frame timestamp: {}", rawTimestamp);
			}
		}
		return latestFrame;
	}

	private Set<Integer> collectParticipantIds(JsonNode participantsNode) {
		Set<Integer> ids = new HashSet<>();
		if (!participantsNode.isArray()) {
			return ids;
		}
		for (JsonNode participant : participantsNode) {
			if (participant.path("participantId").canConvertToInt()) {
				ids.add(participant.path("participantId").asInt());
			}
		}
		return ids;
	}

	private Map<Integer, JsonNode> collectParticipantMeta(JsonNode gameMetadata) {
		Map<Integer, JsonNode> participantMetaById = new HashMap<>();
		collectParticipantMetaFromSide(gameMetadata.path("blueTeamMetadata"), participantMetaById);
		collectParticipantMetaFromSide(gameMetadata.path("redTeamMetadata"), participantMetaById);
		return participantMetaById;
	}

	private void collectParticipantMetaFromSide(JsonNode sideMeta, Map<Integer, JsonNode> collector) {
		JsonNode participants = sideMeta.path("participantMetadata");
		if (!participants.isArray()) {
			return;
		}
		for (JsonNode participant : participants) {
			if (participant.path("participantId").canConvertToInt()) {
				collector.put(participant.path("participantId").asInt(), participant);
			}
		}
	}

	private Integer intOrNull(JsonNode node, String field) {
		JsonNode target = node.path(field);
		return target.isNumber() ? target.asInt() : null;
	}

	private Double doubleOrNull(JsonNode node, String field) {
		JsonNode target = node.path(field);
		return target.isNumber() ? target.asDouble() : null;
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

	private List<Integer> intList(JsonNode arrayNode) {
		List<Integer> values = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return values;
		}
		for (JsonNode item : arrayNode) {
			if (item.isNumber()) {
				values.add(item.asInt());
			}
		}
		return values;
	}

	private String serializeJson(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return "{}";
		}
		try {
			return objectMapper.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}

	private String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second;
	}

	/**
	 * 피드의 진영별 esportsTeamId 로 실제 팀명을 조회한다(team_external_identity). 매핑이 없으면 fallback(매치 기준 이름).
	 * 세트마다 블루/레드가 스왑되므로 매치 고정 이름이 아니라 이 값으로 진영-팀을 맞춰야 한다. esportsTeamId→팀명은 캐시한다.
	 */
	private String resolveTeamName(String esportsTeamId, String fallback) {
		if (esportsTeamId == null || esportsTeamId.isBlank()) {
			return fallback;
		}
		String cached = teamNameByExternalId.get(esportsTeamId);
		if (cached != null) {
			return cached;
		}
		String resolved = teamExternalIdentityRepository
				.findBySourceAndExternalTeamId(LOLESPORTS_SOURCE, esportsTeamId)
				.map(identity -> identity.getTeam().getName())
				.orElse(null);
		if (resolved != null && !resolved.isBlank()) {
			teamNameByExternalId.put(esportsTeamId, resolved);
			return resolved;
		}
		return fallback;
	}
}
