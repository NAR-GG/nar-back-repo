package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteParticipantSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class LiveStateQueryServiceTest {

	private final LiveStateStore liveStateStore = new LiveStateStore();
	private final LiveGameMinuteSnapshotRepository snapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
	private final LiveGameMinuteParticipantSnapshotRepository participantSnapshotRepository =
			mock(LiveGameMinuteParticipantSnapshotRepository.class);
	private final LiveGameObjectEventRepository objectEventRepository = mock(LiveGameObjectEventRepository.class);
	private final RuneMetadataResolver runeMetadataResolver = mock(RuneMetadataResolver.class);
	private final ItemMetadataResolver itemMetadataResolver = mock(ItemMetadataResolver.class);
	private final LiveGameMetadataService liveGameMetadataService = mock(LiveGameMetadataService.class);
	private final LiveStateQueryService service = new LiveStateQueryService(
			liveStateStore,
			snapshotRepository,
			participantSnapshotRepository,
			objectEventRepository,
			new ObjectMapper(),
			runeMetadataResolver,
			itemMetadataResolver,
			liveGameMetadataService);

	@Test
	void objectTimelineIsBoundedByStateFrameTimestamp() {
		LocalDateTime frameTimestampUtc = LocalDateTime.of(2026, 5, 16, 12, 0, 10);
		LiveGameState state = new LiveGameState(
				"game-1",
				"match-1",
				"LCK",
				"Blue",
				"Red",
				frameTimestampUtc.withSecond(0),
				frameTimestampUtc,
				List.of(),
				List.of());
		liveStateStore.putLatestState(state);
		when(liveGameMetadataService.enrich(any(ActiveLiveGame.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		LiveGameObjectEvent dragon = new LiveGameObjectEvent(
				"game-1",
				"match-1",
				"LCK",
				"Blue",
				"DRAGON",
				"FIRE",
				1,
				1,
				frameTimestampUtc.minusSeconds(1));
		when(objectEventRepository
				.findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
						"game-1",
						frameTimestampUtc))
				.thenReturn(List.of(dragon));

		LiveGameState result = service.getLatestState("game-1").orElseThrow();

		assertThat(result.objectTimeline()).hasSize(1);
		assertThat(result.objectTimeline().get(0).sourceFrameTimestampUtc()).isBeforeOrEqualTo(frameTimestampUtc);
		verify(objectEventRepository)
				.findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
						"game-1",
						frameTimestampUtc);
		verify(objectEventRepository, never()).findByGameIdOrderBySourceFrameTimestampUtcAscIdAsc("game-1");
	}

	@Test
	void fallsBackToLatestDbSnapshotWhenMemoryCacheIsEmpty() {
		LocalDateTime frameTimestampUtc = LocalDateTime.of(2026, 5, 17, 8, 18, 9);
		LiveGameMinuteSnapshot snapshot = new LiveGameMinuteSnapshot(
				"game-1",
				frameTimestampUtc.withSecond(0));
		snapshot.updateSnapshot("match-1", "LCK", "KT", "HLE", frameTimestampUtc);
		when(snapshotRepository.findTopByGameIdOrderByMinuteBucketUtcDesc("game-1"))
				.thenReturn(Optional.of(snapshot));
		when(participantSnapshotRepository.findBySnapshot_IdOrderByParticipantIdAsc(null)).thenReturn(List.of());
		when(liveGameMetadataService.enrich(any(ActiveLiveGame.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(objectEventRepository
				.findByGameIdAndSourceFrameTimestampUtcLessThanEqualOrderBySourceFrameTimestampUtcAscIdAsc(
						"game-1",
						frameTimestampUtc))
				.thenReturn(List.of());

		LiveGameState result = service.getLatestState("game-1").orElseThrow();

		assertThat(result.matchId()).isEqualTo("match-1");
		assertThat(result.leagueName()).isEqualTo("LCK");
		assertThat(result.blueTeamName()).isEqualTo("KT");
		assertThat(result.redTeamName()).isEqualTo("HLE");
		assertThat(result.frameTimestampUtc()).isEqualTo(frameTimestampUtc);
	}
}
