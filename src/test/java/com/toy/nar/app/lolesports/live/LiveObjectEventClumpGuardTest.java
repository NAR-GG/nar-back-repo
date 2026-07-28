package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.lolesports.LeagueConfigService;
import com.toy.nar.app.lolesports.live.entity.LiveGameObjectEvent;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.push.TeamLiveEventPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관측 공백 뒤 재개될 때 이벤트가 한 프레임에 뭉쳐 저장되는 것을 막는지 지키는 회귀 테스트.
 *
 * 실제 사고: 업스트림 liveGameIds 지연으로 15~16분간 추적이 끊긴 뒤 재개되자,
 * 끊기기 전 상태와 현재 프레임을 한 번에 diff 해 그 구간 이벤트 전부가 단일 프레임으로 뭉쳤다.
 * 뭉친 킬 델타를 그리디 페어링하면서 같은 킬이 3~4개로 복제돼 저장·발송됐다.
 */
class LiveObjectEventClumpGuardTest {

	private static final DateTimeFormatter FEED_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");

	private final LiveGameObjectEventRepository repository = mock(LiveGameObjectEventRepository.class);
	private final NotificationService notificationService = mock(NotificationService.class);
	private final TeamLiveEventPushService pushService = mock(TeamLiveEventPushService.class);
	private final LeagueConfigService leagueConfigService = mock(LeagueConfigService.class);

	private final LiveObjectEventRecorder recorder =
			new LiveObjectEventRecorder(repository, notificationService, pushService, leagueConfigService);

	@Test
	@DisplayName("정상 간격 프레임은 이벤트를 유도한다")
	void 연속_관측이면_이벤트를_저장한다() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		LocalDateTime base = LocalDateTime.of(2026, 7, 28, 13, 0, 0);

		// 첫 프레임으로 기준을 잡고, 10초 뒤 프레임에서 타워 1개가 늘어난다.
		recorder.record(activeGame(), window(
				frame(base, 2, 0),
				frame(base.plusSeconds(10), 3, 0)));

		verify(repository, atLeastOnce()).save(any(LiveGameObjectEvent.class));
	}

	@Test
	@DisplayName("프레임 간 점프가 비정상이면 이벤트를 유도하지 않는다")
	void 비정상_점프면_저장하지_않는다() {
		LocalDateTime base = LocalDateTime.of(2026, 7, 28, 13, 0, 0);

		// 10초 사이에 타워 6개 + 킬 9개 — 현실적으로 불가능한 증가분(실제 사고 재현).
		recorder.record(activeGame(), window(
				frame(base, 2, 0),
				frame(base.plusSeconds(10), 8, 9)));

		verify(repository, never()).save(any(LiveGameObjectEvent.class));
	}

	@Test
	@DisplayName("프레임 시각 간격이 크게 벌어지면 이벤트를 유도하지 않는다")
	void 프레임_공백이_크면_저장하지_않는다() {
		LocalDateTime base = LocalDateTime.of(2026, 7, 28, 13, 0, 0);

		// 15분 공백 뒤 재개 — 증가분 자체는 작아도 그 사이 프레임을 놓쳤다.
		recorder.record(activeGame(), window(
				frame(base, 2, 0),
				frame(base.plusMinutes(15), 3, 1)));

		verify(repository, never()).save(any(LiveGameObjectEvent.class));
	}

	private ActiveLiveGame activeGame() {
		return new ActiveLiveGame("GAME-1", "MATCH-1", "KESPA", "DNS", "BRO",
				LocalDateTime.now(ZoneOffset.UTC), 0, 3, "blue-id", "red-id");
	}

	private ObjectNode window(ObjectNode... frames) {
		ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.putObject("gameMetadata");
		ArrayNode array = root.putArray("frames");
		for (ObjectNode frame : frames) {
			array.add(frame);
		}
		return root;
	}

	/** blueTeam 에만 타워·킬을 채운 최소 프레임. redTeam 은 전부 0 으로 고정한다. */
	private ObjectNode frame(LocalDateTime timestampUtc, int blueTowers, int blueKills) {
		ObjectNode frame = JsonNodeFactory.instance.objectNode();
		frame.put("rfc460Timestamp", timestampUtc.format(FEED_FORMAT));
		frame.put("gameState", "in_game");
		team(frame.putObject("blueTeam"), blueTowers, blueKills);
		team(frame.putObject("redTeam"), 0, 0);
		return frame;
	}

	private void team(ObjectNode team, int towers, int kills) {
		team.put("towers", towers);
		team.put("barons", 0);
		team.put("inhibitors", 0);
		team.put("totalKills", kills);
		team.putArray("dragons");
		team.putArray("participants");
	}
}
