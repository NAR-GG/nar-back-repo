package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.NaverEsportsScoreClient;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 세트 이벤트 푸시의 bestOf 반영 검증.
 * Bo1(KeSPA Cup 그룹 스테이지 전체)은 세트 개념이 없어 "1세트 종료" 가 아니라 "경기 종료" 여야 하고,
 * payload 의 bestOf·스코어로 앱이 마지막 세트를 판정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamLiveEventPushServiceBestOfTest {

	@Mock
	private MemberDeviceRepository deviceRepository;
	@Mock
	private MemberTeamEventPushDeliveryRepository deliveryRepository;
	@Mock
	private TeamExternalIdentityRepository teamExternalIdentityRepository;
	@Mock
	private LeagueMatchRepository leagueMatchRepository;
	@Mock
	private MobilePushGateway pushGateway;
	@Mock
	private MemberNotificationService notificationService;
	@Mock
	private WorldsService worldsService;
	@Mock
	private NaverEsportsScoreClient naverEsportsScoreClient;
	@Mock
	private QuietAwarePushSender quietAwarePushSender;

	private TeamLiveEventPushService service;

	@BeforeEach
	void setUp() {
		service = new TeamLiveEventPushService(
				deviceRepository, deliveryRepository, teamExternalIdentityRepository,
				leagueMatchRepository, pushGateway, notificationService, worldsService,
				naverEsportsScoreClient, quietAwarePushSender,
				org.mockito.Mockito.mock(com.toy.nar.app.lolesports.LeagueMatchService.class));
		ReflectionTestUtils.setField(service, "fcmNotificationEnabled", true);
		ReflectionTestUtils.setField(service, "scoreRetryAttempts", 1);
		ReflectionTestUtils.setField(service, "scoreRetryDelayMs", 0L);

		Member member = Member.builder().build();
		ReflectionTestUtils.setField(member, "id", 1L);
		MemberDevice device = MemberDevice.builder()
				.member(member)
				.fcmToken("token-1")
				.platform(com.toy.nar.domain.member.entity.MobileDevicePlatform.IOS)
				.build();
		when(deviceRepository.findActiveDevicesBySubscribedMatchId(anyString(), anyString(), any()))
				.thenReturn(List.of(device));
		when(deliveryRepository.reserveAll(any(), anyString(), anyInt(), anyString(), anyLong()))
				.thenReturn(List.of(1L));
		when(pushGateway.isAvailable()).thenReturn(true);
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(1, 0, List.of(), List.of("token-1")), List.of()));
	}

	private LeagueMatch match(Integer bestOf, int blueScore, int redScore) {
		return LeagueMatch.builder()
				.id("m1")
				.leagueName("KESPA")
				.blueTeamCode("BRO")
				.blueTeamName("OK저축은행 브리온")
				.redTeamCode("DNS")
				.redTeamName("DN SOOPers")
				.blueScore(blueScore)
				.redScore(redScore)
				.bestOf(bestOf)
				.matchDate(java.time.LocalDateTime.of(2026, 7, 20, 8, 15))
				.build();
	}

	private MobilePushMessage captureSentMessage() {
		ArgumentCaptor<MobilePushMessage> captor = ArgumentCaptor.forClass(MobilePushMessage.class);
		org.mockito.Mockito.verify(quietAwarePushSender).send(any(), captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("Bo1 세트 종료는 '경기 종료' 로 표기한다")
	void bo1SetEndReadsAsMatchEnd() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 1, 0)));

		service.notifyMatchEvent("SET_END", "m1", 1, null, null, "OK저축은행 브리온", "DN SOOPers");

		MobilePushMessage message = captureSentMessage();
		assertThat(message.title()).isEqualTo("OK저축은행 브리온 vs DN SOOPers 경기 종료");
		assertThat(message.body()).contains("경기 종료").doesNotContain("세트");
	}

	@Test
	@DisplayName("Bo3 세트 종료는 기존처럼 'N세트 종료' 로 표기한다")
	void bo3SetEndKeepsSetWording() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(3, 1, 1)));

		service.notifyMatchEvent("SET_END", "m1", 2, null, null, "OK저축은행 브리온", "DN SOOPers");

		assertThat(captureSentMessage().title()).isEqualTo("OK저축은행 브리온 vs DN SOOPers 2세트 종료");
	}

	@Test
	@DisplayName("payload 에 bestOf 와 스코어를 실어 앱이 마지막 세트를 판정할 수 있게 한다")
	void payloadCarriesBestOfAndScore() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(3, 1, 1)));

		service.notifyMatchEvent("SET_END", "m1", 2, null, null, "OK저축은행 브리온", "DN SOOPers");

		assertThat(captureSentMessage().data())
				.containsEntry("bestOf", "3")
				.containsEntry("blueScore", "1")
				.containsEntry("redScore", "1")
				.containsEntry("setNumber", "2");
	}

	@Test
	@DisplayName("bestOf 를 모르면 관련 키를 넣지 않고 기존 세트 문구를 유지한다")
	void unknownBestOfKeepsLegacyWording() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(null, 1, 1)));

		service.notifyMatchEvent("SET_END", "m1", 2, null, null, "OK저축은행 브리온", "DN SOOPers");

		MobilePushMessage message = captureSentMessage();
		assertThat(message.title()).isEqualTo("OK저축은행 브리온 vs DN SOOPers 2세트 종료");
		assertThat(message.data()).doesNotContainKey("bestOf");
	}

	@Test
	@DisplayName("SET_START 도 bestOf 를 payload 에 싣는다")
	void setStartCarriesBestOf() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(5, 2, 1)));

		service.notifyMatchEvent("SET_START", "m1", 4, null, null, "OK저축은행 브리온", "DN SOOPers");

		assertThat(captureSentMessage().data()).containsEntry("bestOf", "5");
	}
}
