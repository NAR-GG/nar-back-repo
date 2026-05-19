package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteParticipantSnapshot;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteParticipantSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveMinuteSnapshotWriter {

	private final LiveGameMinuteSnapshotRepository snapshotRepository;
	private final LiveGameMinuteParticipantSnapshotRepository participantSnapshotRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public void write(LiveGameState state) {
		LiveGameMinuteSnapshot snapshot = snapshotRepository
				.findByGameIdAndMinuteBucketUtc(state.gameId(), state.minuteBucketUtc())
				.orElseGet(() -> new LiveGameMinuteSnapshot(state.gameId(), state.minuteBucketUtc()));

		if (snapshot.getFrameTimestampUtc() != null && !state.frameTimestampUtc().isAfter(snapshot.getFrameTimestampUtc())) {
			return;
		}

		snapshot.updateSnapshot(
				state.matchId(),
				state.leagueName(),
				state.blueTeamName(),
				state.redTeamName(),
				state.frameTimestampUtc());

		LiveGameMinuteSnapshot savedSnapshot = snapshotRepository.save(snapshot);
		participantSnapshotRepository.deleteBySnapshot_Id(savedSnapshot.getId());
		participantSnapshotRepository.flush();

		List<LiveGameMinuteParticipantSnapshot> participantRows = new ArrayList<>();
		for (LiveParticipantState participant : state.participants()) {
			participantRows.add(new LiveGameMinuteParticipantSnapshot(
					savedSnapshot,
					participant.participantId(),
					participant.teamSide(),
					participant.role(),
					participant.playerName(),
					participant.esportsPlayerId(),
					participant.championName(),
					participant.level(),
					participant.kills(),
					participant.deaths(),
					participant.assists(),
					participant.totalGoldEarned(),
					participant.creepScore(),
					participant.killParticipation(),
					participant.championDamageShare(),
					toJson(participant.itemIds()),
					participant.perksJson()));
		}

		participantSnapshotRepository.saveAllAndFlush(participantRows);
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			log.warn("Failed to serialize live participant field: {}", e.getMessage());
			return "[]";
		}
	}
}
