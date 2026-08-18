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

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
 * 구독자 팬아웃이 발송·DB 왕복을 상수로 묶는지 지키는 회귀 테스트.
 *
 * 예전엔 구독자마다 pushGateway.send 와 reserve/markSent 를 한 건씩 돌았다.
 * 실측: 2026-07-29 구독자 1,548명 SET_START 이 1,074초(1명당 0.69초),
 * FCM 배치화 후 2026-07-30 구독자 747명이 37초(1명당 0.05초 = DB 왕복 비용).
 * 앱→DB 왕복이 6.5ms 라 구독자 단위 쿼리가 남아 있으면 시간이 구독자 수에 비례한다.
 */
class TeamLiveEventPushFanOutBatchTest {

	private static final String MATCH_ID = "MATCH-1";
	private static final int SET_NUMBER = 2;

	private final MemberDeviceRepository deviceRepository = mock(MemberDeviceRepository.class);
	private final MemberTeamEventPushDeliveryRepository deliveryRepository =
			mock(MemberTeamEventPushDeliveryRepository.class);
	private final MobilePushGateway pushGateway = mock(MobilePushGateway.class);
	private final MemberNotificationService notificationService = mock(MemberNotificationService.class);
	private final QuietAwarePushSender quietAwarePushSender = mock(QuietAwarePushSender.class);

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
				mock(NaverEsportsScoreClient.class),
				quietAwarePushSender,
				mock(com.toy.nar.app.lolesports.LeagueMatchService.class));
		ReflectionTestUtils.setField(service, "fcmNotificationEnabled", true);
		when(pushGateway.isAvailable()).thenReturn(true);
	}

	@Test
	@DisplayName("구독자 3명이어도 발송 1회·예약 1회·마감 1회로 끝낸다")
	void 구독자_수와_무관하게_호출_횟수가_상수다() {
		givenSubscribers(List.of(device(1L, "token-1"), device(2L, "token-2"), device(3L, "token-3")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(1L, 2L, 3L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(3, 0, List.of(), List.of("token-1", "token-2", "token-3")), List.of()));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 10L, null, "KILL", "제목", "본문");

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-1", "token-2", "token-3");

		verify(deliveryRepository, times(1)).reserveAll(any(), eq(MATCH_ID), eq(SET_NUMBER), anyString(), anyLong());
		verify(deliveryRepository, times(1)).markSentAll(any(), eq(MATCH_ID), eq(SET_NUMBER), anyString(), anyLong());
		verify(notificationService, times(1)).recordAll(any(), any(), anyString(), any(), any());
		// 구독자 단위 호출이 남아 있으면 안 된다.
		verify(deliveryRepository, never()).reserve(anyLong(), anyString(), anyInt(), anyString(), anyLong());
		verify(deliveryRepository, never()).markSent(anyLong(), anyString(), anyInt(), anyString(), anyLong());
	}

	@Test
	@DisplayName("토큰별 결과로 구독자를 갈라 마감한다")
	void 성공한_토큰의_구독자만_발송_기록한다() {
		givenSubscribers(List.of(device(1L, "token-1"), device(2L, "token-2")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(1L, 2L));
		// token-2 만 성공
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(1, 1, List.of(), List.of("token-2")), List.of()));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 11L, null, "KILL", "제목", "본문");

		assertThat(capturedIds(sentCaptor())).containsExactly(2L);
		assertThat(capturedIds(failedCaptor())).containsExactly(1L);
	}

	@Test
	@DisplayName("예약에서 빠진 구독자는 토큰에 포함하지 않는다")
	void 예약되지_않은_구독자는_발송_대상에서_빠진다() {
		givenSubscribers(List.of(device(1L, "token-1"), device(2L, "token-2")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(2L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(1, 0, List.of(), List.of("token-2")), List.of()));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 12L, null, "KILL", "제목", "본문");

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-2");
	}

	@Test
	@DisplayName("예약 대상이 없으면 발송하지 않는다")
	void 예약_대상이_없으면_발송하지_않는다() {
		givenSubscribers(List.of(device(1L, "token-1")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of());

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 13L, null, "KILL", "제목", "본문");

		verify(quietAwarePushSender, never()).send(any(), any());
	}

	@Test
	@DisplayName("발송은 회원별 토큰맵으로 sender 에 위임하고 게이트웨이를 직접 부르지 않는다")
	void 발송은_sender_에_위임한다() {
		givenSubscribers(List.of(device(1L, "token-1"), device(2L, "token-2")));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(1L, 2L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(2, 0, List.of(), List.of("token-1", "token-2")), List.of()));

		service.notifyLiveEvent(MATCH_ID, SET_NUMBER, 14L, null, "KILL", "제목", "본문");

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue())
				.containsExactlyInAnyOrderEntriesOf(Map.of(1L, List.of("token-1"), 2L, List.of("token-2")));
		verify(pushGateway, never()).send(any(), any());
	}

	private void givenSubscribers(List<MemberDevice> devices) {
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(MATCH_ID, TeamLiveEventPushService.TYPE_LIVE_EVENT, "KILL"))
				.thenReturn(devices);
	}

	private ArgumentCaptor<Collection<Long>> sentCaptor() {
		ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(deliveryRepository).markSentAll(captor.capture(), anyString(), anyInt(), anyString(), anyLong());
		return captor;
	}

	private ArgumentCaptor<Collection<Long>> failedCaptor() {
		ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(deliveryRepository)
				.markFailedAll(captor.capture(), anyString(), anyInt(), anyString(), anyLong(), anyString());
		return captor;
	}

	private List<Long> capturedIds(ArgumentCaptor<Collection<Long>> captor) {
		return List.copyOf(captor.getValue());
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
