package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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
	void withTeamAvoidsDuplicatingTeamTagAlreadyInPlayerName() {
		// summonerName 이 팀 태그를 포함하면("T1 Doran") 팀명을 다시 붙이지 않는다.
		assertThat(invokeWithTeam("T1", "T1 Doran")).isEqualTo("T1 Doran");
		assertThat(invokeWithTeam("T1", "t1 doran")).isEqualTo("t1 doran"); // 대소문자 무시
		// 태그가 없거나 다르면 팀명을 붙인다.
		assertThat(invokeWithTeam("HLE", "Zeka")).isEqualTo("HLE Zeka");
		// 선수명이 팀명과 완전히 같으면 팀명만.
		assertThat(invokeWithTeam("T1", "T1")).isEqualTo("T1");
		// 선수명이 비면 팀명만.
		assertThat(invokeWithTeam("T1", null)).isEqualTo("T1");
	}

	private String invokeWithTeam(String team, String player) {
		return (String) ReflectionTestUtils.invokeMethod(recorder, "withTeam", team, player);
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
