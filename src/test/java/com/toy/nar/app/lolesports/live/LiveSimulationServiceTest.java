package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.dto.LiveSimulationResponse;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSimulationServiceTest {

	@Test
	void replaysPastFramesAndReportsProcessedRange() throws Exception {
		LiveStatsClient liveStatsClient = mock(LiveStatsClient.class);
		LiveFrameProcessor liveFrameProcessor = mock(LiveFrameProcessor.class);
		LeagueMatchGameRepository leagueMatchGameRepository = mock(LeagueMatchGameRepository.class);
		LiveGameMetadataService liveGameMetadataService = mock(LiveGameMetadataService.class);
		LiveSimulationService service = new LiveSimulationService(
				liveStatsClient,
				liveFrameProcessor,
				leagueMatchGameRepository,
				liveGameMetadataService);
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode responseWithFrames = objectMapper.readTree("{\"frames\":[{}]}");
		ActiveLiveGame activeGame = new ActiveLiveGame(
				"game-1",
				null,
				null,
				null,
				null,
				LocalDateTime.of(2026, 5, 29, 12, 0),
				0);
		when(leagueMatchGameRepository.findWithMatchByGameId("game-1")).thenReturn(Optional.empty());
		when(liveGameMetadataService.enrich(any(ActiveLiveGame.class))).thenReturn(activeGame);
		when(liveStatsClient.getWindow(anyString(), anyString())).thenReturn(responseWithFrames);
		when(liveStatsClient.getDetails(anyString(), anyString())).thenReturn(responseWithFrames);
		when(liveFrameProcessor.process(any(), any(), any()))
				.thenReturn(
						Optional.of(stateAt("2026-05-29T12:00:10")),
						Optional.of(stateAt("2026-05-29T12:00:20")),
						Optional.of(stateAt("2026-05-29T12:00:30")));

		LiveSimulationResponse response = service.simulate(
				"game-1",
				"2026-05-29T12:00:00Z",
				3,
				10);

		assertThat(response.processedFrames()).isEqualTo(3);
		assertThat(response.emptyResponses()).isZero();
		assertThat(response.failures()).isZero();
		assertThat(response.firstFrameTimestampUtc()).isEqualTo(LocalDateTime.parse("2026-05-29T12:00:10"));
		assertThat(response.lastFrameTimestampUtc()).isEqualTo(LocalDateTime.parse("2026-05-29T12:00:30"));
		verify(liveStatsClient).getWindow("game-1", "2026-05-29T12:00:00Z");
		verify(liveStatsClient).getWindow("game-1", "2026-05-29T12:00:10Z");
		verify(liveStatsClient).getWindow("game-1", "2026-05-29T12:00:20Z");
	}

	private LiveGameState stateAt(String timestamp) {
		LocalDateTime frameTimestamp = LocalDateTime.parse(timestamp);
		return new LiveGameState(
				"game-1",
				"match-1",
				"LCK",
				"DK",
				"BRO",
				frameTimestamp.withSecond(0),
				frameTimestamp,
				List.of(),
				List.of());
	}
}
