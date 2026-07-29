package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.NaverEsportsScoreClient;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberTeamEventPushDeliveryRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 구독자 팬아웃이 발송 호출 1회로 묶이는지 지키는 회귀 테스트.
 *
 * 예전엔 구독자마다 pushGateway.send 를 호출했다. 실측 2026-07-29 LCK T1 vs KT 에서
 * 구독자 약 1,500명 팬아웃이 이벤트당 8~18분 걸려 마지막 구독자는 세트가 끝난 뒤 알림을 받았다.
 */
class TeamLiveEventPushFanOutBatchTest {

	private static final String MATCH_ID = "MATCH-1";
	private static final int SET_NUMBER = 2;

	private final MemberDeviceRepository deviceRepository = mock(MemberDeviceRepository.class);
	private final MemberTeamEventPushDeliveryRepository deliveryRepository =
			mock(MemberTeamEventPushDeliveryRepository.class);
	private final MobilePushGateway pushGateway = mock(MobilePushGateway.class);
	private final MemberNotificationService notificationService = mock(MemberNotificationService.class);

	private TeamLiveEventPushService service;

	@BeforeEach
	void setUp() {
		service = new TeamLiveEventPushService(
				deviceRepository,
				deliveryRepository,
				mock(TeamExternalIdentityRepository.class),
				mock(LeagueMatchRepository.class),
				pushGateway,
				notificationService,
				mock(WorldsService.class),
				mock(NaverEsportsScoreClient.class));
		ReflectionTestUtils.setField(service, "fcmNotificationEnabled", true);
		when(pushGateway.isAvailable()).thenReturn(true);
	}

	@Test
	@DisplayName("구독자 3명이어도 발송 호출은 1회, 토큰은 한 번에 넘긴다")
	void 구독자_전원을_한_번에_발송한다() {
		List<MemberDevice> devices = List.of(device(1L, "token-1"), device(2L, "token-2"), device(3L, "token-3"));
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(MATCH_ID, TeamLiveEventPushService.TYPE_LIVE_EVENT))
				.thenReturn(devices);
		when(deliveryRepository.reserve(anyLong(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(true);
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of("token-1", "token-2", "token-3")));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 10L, null, "제목", "본문");

		ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
		verify(pushGateway, times(1)).send(tokens.capture(), any());
		assertThat(tokens.getValue()).containsExactly("token-1", "token-2", "token-3");
		verify(deliveryRepository, times(3))
				.markSent(anyLong(), eq(MATCH_ID), eq(SET_NUMBER), anyString(), anyLong());
	}

	@Test
	@DisplayName("토큰별 결과로 구독자를 갈라 기록한다")
	void 성공한_토큰의_구독자만_발송_기록한다() {
		List<MemberDevice> devices = List.of(device(1L, "token-1"), device(2L, "token-2"));
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(MATCH_ID, TeamLiveEventPushService.TYPE_LIVE_EVENT))
				.thenReturn(devices);
		when(deliveryRepository.reserve(anyLong(), anyString(), anyInt(), anyString(), anyLong())).thenReturn(true);
		// token-2 만 성공
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(1, 1, List.of(), List.of("token-2")));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 11L, null, "제목", "본문");

		verify(deliveryRepository).markSent(eq(2L), eq(MATCH_ID), eq(SET_NUMBER), anyString(), anyLong());
		verify(deliveryRepository, never()).markSent(eq(1L), anyString(), anyInt(), anyString(), anyLong());
		verify(deliveryRepository).markFailed(eq(1L), eq(MATCH_ID), eq(SET_NUMBER), anyString(), anyLong(), anyString());
	}

	@Test
	@DisplayName("dedup 에 막힌 구독자는 토큰에 포함하지 않는다")
	void 예약_실패_구독자는_발송_대상에서_빠진다() {
		List<MemberDevice> devices = List.of(device(1L, "token-1"), device(2L, "token-2"));
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(MATCH_ID, TeamLiveEventPushService.TYPE_LIVE_EVENT))
				.thenReturn(devices);
		when(deliveryRepository.reserve(eq(1L), anyString(), anyInt(), anyString(), anyLong())).thenReturn(false);
		when(deliveryRepository.reserve(eq(2L), anyString(), anyInt(), anyString(), anyLong())).thenReturn(true);
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(1, 0, List.of(), List.of("token-2")));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 12L, null, "제목", "본문");

		ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
		verify(pushGateway).send(tokens.capture(), any());
		assertThat(tokens.getValue()).containsExactly("token-2");
	}

	private MemberDevice device(Long memberId, String token) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		MemberDevice device = mock(MemberDevice.class);
		when(device.getMember()).thenReturn(member);
		when(device.getFcmToken()).thenReturn(token);
		return device;
	}
}
