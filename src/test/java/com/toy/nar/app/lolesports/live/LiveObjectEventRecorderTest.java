package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveObjectEventRecorderTest {

	private final LiveGameObjectEventRepository objectEventRepository = mock(LiveGameObjectEventRepository.class);
	private final LiveObjectEventRecorder recorder = new LiveObjectEventRecorder(
			objectEventRepository, mock(NotificationService.class), mock(TeamLiveEventPushService.class));
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void replayingSameWindowDoesNotSaveDuplicateObjectEvents() throws Exception {
		when(objectEventRepository.existsByGameIdAndTeamSideAndEventTypeAndEventOrder(any(), any(), any(), any()))
				.thenReturn(false);
		ActiveLiveGame game = new ActiveLiveGame("game-1", "match-1", "LCK", "KT", "HLE",
				LocalDateTime.now(ZoneOffset.UTC), 0);
		JsonNode window = objectMapper.readTree("""
				{
				  "frames": [
				    {
				      "rfc460Timestamp": "2026-05-17T08:18:00.000Z",
				      "blueTeam": { "towers": 0, "barons": 0, "inhibitors": 0, "dragons": [] },
				      "redTeam": { "towers": 0, "barons": 0, "inhibitors": 0, "dragons": [] }
				    },
				    {
				      "rfc460Timestamp": "2026-05-17T08:18:10.000Z",
				      "blueTeam": { "towers": 1, "barons": 0, "inhibitors": 0, "dragons": ["mountain"] },
				      "redTeam": { "towers": 0, "barons": 0, "inhibitors": 0, "dragons": [] }
				    }
				  ]
				}
				""");

		recorder.record(game, window);
		recorder.record(game, window);

		verify(objectEventRepository, times(2)).save(any(LiveGameObjectEvent.class));
	}
}
