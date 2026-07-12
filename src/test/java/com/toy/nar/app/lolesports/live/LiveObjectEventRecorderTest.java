package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
	void killPushShowsWindowSideTeamsWhenSidesSwapBetweenSets() throws Exception {
		TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
		when(pushService.isEnabled()).thenReturn(true);
		LiveObjectEventRecorder swapRecorder = new LiveObjectEventRecorder(
				objectEventRepository, mock(NotificationService.class), pushService);
		ReflectionTestUtils.setField(swapRecorder, "notificationLeagues", "LCK");
		when(objectEventRepository.existsByGameIdAndTeamSideAndEventTypeAndEventOrder(any(), any(), any(), any()))
				.thenReturn(false);
		when(objectEventRepository.save(any(LiveGameObjectEvent.class))).thenAnswer(invocation -> {
			LiveGameObjectEvent saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 42L);
			return saved;
		});

		// 매치 기준 Blue=HLE, Red=BLG. 이번 세트는 진영이 스왑되어 window 기준 Blue=BLG, Red=HLE.
		ActiveLiveGame game = new ActiveLiveGame("game-2", "match-2", "LCK",
				"Hanwha Life Esports", "BILIBILI GAMING",
				LocalDateTime.now(ZoneOffset.UTC), 0,
				4, "team-hle", "team-blg");
		JsonNode window = objectMapper.readTree("""
				{
				  "gameMetadata": {
				    "blueTeamMetadata": {
				      "esportsTeamId": "team-blg",
				      "participantMetadata": [
				        { "participantId": 1, "summonerName": "ON", "championId": "Alistar" }
				      ]
				    },
				    "redTeamMetadata": {
				      "esportsTeamId": "team-hle",
				      "participantMetadata": [
				        { "participantId": 6, "summonerName": "Zeus", "championId": "Rumble" }
				      ]
				    }
				  },
				  "frames": [
				    {
				      "rfc460Timestamp": "2026-07-09T11:20:00.000Z",
				      "blueTeam": { "totalKills": 0, "dragons": [],
				        "participants": [ { "participantId": 1, "kills": 0, "deaths": 0 } ] },
				      "redTeam": { "totalKills": 1, "dragons": [],
				        "participants": [ { "participantId": 6, "kills": 1, "deaths": 0 } ] }
				    },
				    {
				      "rfc460Timestamp": "2026-07-09T11:20:10.000Z",
				      "blueTeam": { "totalKills": 1, "dragons": [],
				        "participants": [ { "participantId": 1, "kills": 1, "deaths": 0 } ] },
				      "redTeam": { "totalKills": 1, "dragons": [],
				        "participants": [ { "participantId": 6, "kills": 1, "deaths": 1 } ] }
				    }
				  ]
				}
				""");

		swapRecorder.record(game, window);

		// 킬러 ON 은 window 기준 Blue=BLG 소속 → 구독 대상 팀은 team-blg.
		// 문구는 선수명(summonerName)만 쓴다 — 태그가 이미 포함되고 정식 팀명을 붙이면 중복돼 보인다.
		ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
		verify(pushService).notifyLiveEvent(
				eq("match-2"), eq(4), anyLong(), eq("team-blg"), title.capture(), any());
		assertThat(title.getValue()).isEqualTo("ON님이 Zeus님을 처치했습니다");
	}

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
