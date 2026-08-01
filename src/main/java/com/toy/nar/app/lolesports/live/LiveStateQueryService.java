package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveObjectEventResponse;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteParticipantSnapshot;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteParticipantSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStateQueryService {

	private final LiveStateStore liveStateStore;
	private final LiveGameMinuteSnapshotRepository snapshotRepository;
	private final LiveGameMinuteParticipantSnapshotRepository participantSnapshotRepository;
	private final LiveGameObjectEventRepository objectEventRepository;
	private final ObjectMapper objectMapper;
	private final RuneMetadataResolver runeMetadataResolver;
	private final ItemMetadataResolver itemMetadataResolver;
	private final LiveGameMetadataService liveGameMetadataService;

	public Optional<LiveGameState> getLatestState(String gameId) {
		Optional<LiveGameState> inMemory = liveStateStore.getLatestState(gameId);
		if (inMemory.isPresent()) {
			return Optional.of(enrichObjectTimeline(enrichMetadata(inMemory.get())));
		}

		return snapshotRepository.findTopByGameIdOrderByMinuteBucketUtcDesc(gameId)
				.map(this::toState)
				.map(this::enrichMetadata)
				.map(this::enrichObjectTimeline);
	}

	public List<LiveGameState> getRecentMinuteSnapshots(String gameId) {
		return snapshotRepository.findTop60ByGameIdOrderByMinuteBucketUtcDesc(gameId).stream()
				.map(this::toState)
				.toList();
	}

	private LiveGameState toState(LiveGameMinuteSnapshot snapshot) {
		List<LiveParticipantState> participants = participantSnapshotRepository
				.findBySnapshot_IdOrderByParticipantIdAsc(snapshot.getId()).stream()
				.map(this::toParticipantState)
				.toList();

		return new LiveGameState(
				snapshot.getGameId(),
				snapshot.getMatchId(),
				snapshot.getLeagueName(),
				snapshot.getBlueTeamName(),
				snapshot.getRedTeamName(),
				snapshot.getMinuteBucketUtc(),
				snapshot.getFrameTimestampUtc(),
				participants,
				List.of());
	}

	private LiveGameState enrichObjectTimeline(LiveGameState state) {
		List<LiveGameObjectEvent> objectEvents = state.frameTimestampUtc() == null
				? objectEventRepository.findByGameIdOrderBySourceFrameTimestampUtcAscIdAsc(state.gameId())
				: objectEventRepository
						.findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
								state.gameId(),
								state.frameTimestampUtc());
		List<LiveObjectEventResponse> timeline = objectEvents.stream()
				.map(this::toObjectEventResponse)
				.toList();

		return new LiveGameState(
				state.gameId(),
				state.matchId(),
				state.leagueName(),
				state.blueTeamName(),
				state.redTeamName(),
				state.minuteBucketUtc(),
				state.frameTimestampUtc(),
				state.participants(),
				timeline);
	}

	private LiveGameState enrichMetadata(LiveGameState state) {
		// 메타데이터가 이미 완비면 resolve 생략. league_match_game에 매핑 없는 게임은
		// resolve가 라이브 리그 전체 스케줄 API 순회(수 초)로 빠지는데, 채울 것도 없이 낭비다.
		if (hasCompleteMetadata(state)) {
			return state;
		}
		ActiveLiveGame metadata = liveGameMetadataService.enrich(new ActiveLiveGame(
				state.gameId(),
				state.matchId(),
				state.leagueName(),
				state.blueTeamName(),
				state.redTeamName(),
				null,
				0));
		return new LiveGameState(
				state.gameId(),
				metadata.matchId(),
				metadata.leagueName(),
				metadata.blueTeamName(),
				metadata.redTeamName(),
				state.minuteBucketUtc(),
				state.frameTimestampUtc(),
				state.participants(),
				state.objectTimeline());
	}

	private boolean hasCompleteMetadata(LiveGameState state) {
		return !isBlank(state.matchId())
				&& !isBlank(state.leagueName())
				&& !isBlank(state.blueTeamName())
				&& !isBlank(state.redTeamName());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private LiveObjectEventResponse toObjectEventResponse(LiveGameObjectEvent event) {
		return new LiveObjectEventResponse(
				event.getTeamSide(),
				event.getEventType(),
				event.getEventSubType(),
				event.getEventOrder(),
				event.getValueAfter(),
				event.getSourceFrameTimestampUtc());
	}

	private LiveParticipantState toParticipantState(LiveGameMinuteParticipantSnapshot participant) {
		List<Integer> itemIds = parseItemIds(participant.getItemIdsJson());
		PerkInfo perkInfo = extractPerkInfo(participant.getPerksJson());
		return new LiveParticipantState(
				participant.getParticipantId(),
				participant.getTeamSide(),
				participant.getRole(),
				participant.getPlayerName(),
				participant.getEsportsPlayerId(),
				participant.getChampionName(),
				participant.getLevel(),
				participant.getKills(),
				participant.getDeaths(),
				participant.getAssists(),
				participant.getTotalGoldEarned(),
				participant.getCreepScore(),
				participant.getKillParticipation(),
				participant.getChampionDamageShare(),
				itemIds,
				itemMetadataResolver.resolveItemNames(itemIds),
				itemMetadataResolver.resolveItemImageUrls(itemIds),
				participant.getPerksJson(),
				perkInfo.primaryStyleName(),
				perkInfo.subStyleName(),
				perkInfo.runeNames());
	}

	private List<Integer> parseItemIds(String itemIdsJson) {
		if (itemIdsJson == null || itemIdsJson.isBlank()) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(itemIdsJson, new TypeReference<>() {
			});
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	private PerkInfo extractPerkInfo(String perksJson) {
		if (perksJson == null || perksJson.isBlank() || "{}".equals(perksJson)) {
			return new PerkInfo(null, null, List.of());
		}
		try {
			com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(perksJson);
			Integer primaryStyleId = node.path("styleId").isNumber() ? node.path("styleId").asInt() : null;
			Integer subStyleId = node.path("subStyleId").isNumber() ? node.path("subStyleId").asInt() : null;

			List<Integer> runeIds = new java.util.ArrayList<>();
			com.fasterxml.jackson.databind.JsonNode perks = node.path("perks");
			if (perks.isArray()) {
				for (com.fasterxml.jackson.databind.JsonNode runeId : perks) {
					if (runeId.isNumber()) {
						runeIds.add(runeId.asInt());
					}
				}
			}

			return new PerkInfo(
					runeMetadataResolver.resolveStyleName(primaryStyleId),
					runeMetadataResolver.resolveStyleName(subStyleId),
					runeMetadataResolver.resolveRuneNames(runeIds));
		} catch (IOException e) {
			return new PerkInfo(null, null, List.of());
		}
	}

	private record PerkInfo(String primaryStyleName, String subStyleName, List<String> runeNames) {
	}
}
