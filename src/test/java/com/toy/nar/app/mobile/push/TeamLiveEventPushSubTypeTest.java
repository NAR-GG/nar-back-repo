package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.NaverEsportsScoreClient;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberTeamEventPushDeliveryRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 라이브 이벤트 종류가 구독 조회까지 전달되는지, 그리고 FCM {@code type} 이 바뀌지 않는지 지킨다.
 *
 * <p>{@code type} 은 앱이 알림을 분류하는 값이다({@code fcm_notification_types.dart}).
 * 여기에 KILL 같은 세부 종류를 넣으면 배포된 구버전이 알림을 분류하지 못한다.</p>
 */
class TeamLiveEventPushSubTypeTest {

	private static final String MATCH_ID = "MATCH-1";

	private final MemberDeviceRepository deviceRepository = mock(MemberDeviceRepository.class);
	private final MemberTeamEventPushDeliveryRepository deliveryRepository =
			mock(MemberTeamEventPushDeliveryRepository.class);
	private final MobilePushGateway pushGateway = mock(MobilePushGateway.class);
	private final TeamExternalIdentityRepository teamIdentityRepository =
			mock(TeamExternalIdentityRepository.class);
	private final LeagueMatchRepository leagueMatchRepository = mock(LeagueMatchRepository.class);
	private final MemberNotificationService notificationService = mock(MemberNotificationService.class);
	private final NaverEsportsScoreClient naverScoreClient = mock(NaverEsportsScoreClient.class);
	private final WorldsService worldsService = mock(WorldsService.class);
	private final QuietAwarePushSender quietAwarePushSender = mock(QuietAwarePushSender.class);

	private TeamLiveEventPushService service;

	@BeforeEach
	void setUp() {
		service = new TeamLiveEventPushService(
				deviceRepository,
				deliveryRepository,
				teamIdentityRepository,
				leagueMatchRepository,
				pushGateway,
				notificationService,
				worldsService,
				naverScoreClient,
				quietAwarePushSender,
				mock(com.toy.nar.app.lolesports.LeagueMatchService.class));
		ReflectionTestUtils.setField(service, "fcmNotificationEnabled", true);
		when(pushGateway.isAvailable()).thenReturn(true);
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(any(), any(), any()))
				.thenReturn(List.of());
	}

	@Test
	@DisplayName("킬 이벤트는 KILL 로 구독 조회한다")
	void 킬은_KILL_로_조회한다() {
		service.notifyLiveEvent(MATCH_ID, 1, 10L, null, "KILL", "제목", "본문");

		verify(deviceRepository).findActiveDevicesBySubscribedMatchId(
				eq(MATCH_ID), eq(TeamLiveEventPushService.TYPE_LIVE_EVENT), eq("KILL"));
	}

	@Test
	@DisplayName("바론 이벤트는 BARON 으로 구독 조회한다")
	void 바론은_BARON_으로_조회한다() {
		service.notifyLiveEvent(MATCH_ID, 1, 11L, null, "BARON", "제목", "본문");

		verify(deviceRepository).findActiveDevicesBySubscribedMatchId(
				eq(MATCH_ID), eq(TeamLiveEventPushService.TYPE_LIVE_EVENT), eq("BARON"));
	}

	/**
	 * 종류를 모르는 이벤트(앞으로 추가될 아타칸 등)는 마스터 스위치만 보고 보낸다.
	 * 컬럼이 없다고 알림이 조용히 사라지면 안 된다.
	 */
	@Test
	@DisplayName("종류가 없으면 null 로 조회해 마스터 스위치만 본다")
	void 종류가_없으면_null_로_조회한다() {
		service.notifyLiveEvent(MATCH_ID, 1, 12L, null, null, "제목", "본문");

		verify(deviceRepository).findActiveDevicesBySubscribedMatchId(
				eq(MATCH_ID), eq(TeamLiveEventPushService.TYPE_LIVE_EVENT), eq(null));
	}
}
