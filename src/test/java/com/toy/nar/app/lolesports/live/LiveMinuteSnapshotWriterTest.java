package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.lolesports.live.entity.LiveGameMinuteSnapshot;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteParticipantSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveMinuteSnapshotWriterTest {

	private final LiveGameMinuteSnapshotRepository snapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
	private final LiveGameMinuteParticipantSnapshotRepository participantSnapshotRepository =
			mock(LiveGameMinuteParticipantSnapshotRepository.class);
	private final LiveMinuteSnapshotWriter writer = new LiveMinuteSnapshotWriter(
			snapshotRepository,
			participantSnapshotRepository,
			new ObjectMapper());

	@Test
	void sameOrOlderFrameDoesNotRewriteSnapshot() {
		LocalDateTime minute = LocalDateTime.of(2026, 5, 17, 8, 18);
		LiveGameMinuteSnapshot existing = new LiveGameMinuteSnapshot("game-1", minute);
		existing.updateSnapshot("match-1", "LCK", "KT", "HLE", minute.plusSeconds(10));
		when(snapshotRepository.findByGameIdAndMinuteBucketUtc("game-1", minute)).thenReturn(Optional.of(existing));

		writer.write(state("game-1", minute, minute.plusSeconds(10)));

		verify(snapshotRepository, never()).save(any());
		verify(participantSnapshotRepository, never()).deleteBySnapshot_Id(any());
		verify(participantSnapshotRepository, never()).saveAllAndFlush(any());
	}

	private LiveGameState state(String gameId, LocalDateTime minute, LocalDateTime frameTimestampUtc) {
		return new LiveGameState(
				gameId,
				"match-1",
				"LCK",
				"KT",
				"HLE",
				minute,
				frameTimestampUtc,
				List.of(new LiveParticipantState(
						1,
						"Blue",
						"top",
						"KT PerfecT",
						"player-1",
						"Zaahen",
						11,
						0,
						0,
						0,
						4307,
						120,
						0.0,
						0.22,
						List.of(2031, 3078),
						List.of(),
						List.of(),
						"{}",
						null,
						null,
						List.of())),
				List.of());
	}
}
