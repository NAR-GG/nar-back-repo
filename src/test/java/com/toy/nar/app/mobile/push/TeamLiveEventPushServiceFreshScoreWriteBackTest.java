package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.NaverEsportsScoreClient;
import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberTeamEventPushDeliveryRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SET_END 가 재조회로 얻은 신선 스코어를 DB 에 선반영하는지.
 *
 * <p>지금까지 재조회 결과는 푸시 문구에만 쓰였다. 잠금화면 카드는 그 직후 DB 를 그대로 읽으므로
 * (LivePollingScheduler.pushLiveActivitySetEnd) 아직 갱신 안 된 스코어가 카드에 실렸다 —
 * 실측 2026-08-13 KRX vs BFX 세트2: 카드 발송 18:29:16(DB 1:0), 선반영 18:29:28(1:1).
 * 카드에는 라벨만 "3세트 준비 중"으로 맞고 숫자는 1:0 으로 남았고, 세트3 시작까지 그대로였다.
 * 알림 문구는 정확했다 — 두 경로가 서로 다른 스코어를 본 셈이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TeamLiveEventPushServiceFreshScoreWriteBackTest {

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
	private LeagueMatchService leagueMatchService;

	private TeamLiveEventPushService service;

	@BeforeEach
	void setUp() {
		service = new TeamLiveEventPushService(
				deviceRepository, deliveryRepository, teamExternalIdentityRepository,
				leagueMatchRepository, pushGateway, notificationService, worldsService,
				naverEsportsScoreClient, mock(QuietAwarePushSender.class), leagueMatchService);
		ReflectionTestUtils.setField(service, "scoreRetryAttempts", 3);
		ReflectionTestUtils.setField(service, "scoreRetryDelayMs", 0L);
	}

	@Test
	@DisplayName("네이버 재조회로 얻은 스코어는 DB 에 선반영한다 — 직후 카드가 같은 값을 읽는다")
	void writesBackNaverScore() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 0)));
		when(naverEsportsScoreClient.fetchScore(eq("KC"), eq("T1"), any()))
				.thenReturn(new int[] { 1, 1 });

		service.buildMatchScoreLine("m1", 2);

		verify(leagueMatchService).applyFreshScore("m1", new int[] { 1, 1 });
	}

	@Test
	@DisplayName("업스트림(Riot) 재조회로 얻은 스코어도 선반영한다")
	void writesBackUpstreamScore() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 0)));
		when(naverEsportsScoreClient.fetchScore(eq("KC"), eq("T1"), any())).thenReturn(null);
		when(worldsService.fetchMatchGameWins("m1")).thenReturn(new int[] { 2, 0 });

		service.buildMatchScoreLine("m1", 2);

		verify(leagueMatchService).applyFreshScore("m1", new int[] { 2, 0 });
	}

	@Test
	@DisplayName("DB 가 이미 신선하면 선반영하지 않는다 — 쓸 것이 없다")
	void skipsWriteBackWhenDbAlreadyFresh() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 1)));

		service.buildMatchScoreLine("m1", 2);

		verify(leagueMatchService, never()).applyFreshScore(any(), any());
	}

	@Test
	@DisplayName("재조회가 끝까지 stale 이면 선반영하지 않는다 — 틀린 값을 쓰면 카드까지 오염된다")
	void skipsWriteBackWhenStillStale() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 0)));
		when(naverEsportsScoreClient.fetchScore(eq("KC"), eq("T1"), any()))
				.thenReturn(new int[] { 1, 0 });
		when(worldsService.fetchMatchGameWins("m1")).thenReturn(new int[] { 1, 0 });

		service.buildMatchScoreLine("m1", 2);

		verify(leagueMatchService, never()).applyFreshScore(any(), any());
	}

	@Test
	@DisplayName("선반영이 터져도 푸시 문구는 정상으로 만든다")
	void pushLineSurvivesWriteBackFailure() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(1, 0)));
		when(naverEsportsScoreClient.fetchScore(eq("KC"), eq("T1"), any()))
				.thenReturn(new int[] { 1, 1 });
		org.mockito.Mockito.doThrow(new RuntimeException("db down"))
				.when(leagueMatchService).applyFreshScore(any(), any());

		org.assertj.core.api.Assertions.assertThat(service.buildMatchScoreLine("m1", 2))
				.isEqualTo("Karmine Corp 1 vs 1 T1");
	}

	private LeagueMatch match(Integer blueScore, Integer redScore) {
		return LeagueMatch.builder()
				.id("m1")
				.leagueName("LCK")
				.blueTeamCode("KC")
				.blueTeamName("Karmine Corp")
				.redTeamCode("T1")
				.redTeamName("T1")
				.blueScore(blueScore)
				.redScore(redScore)
				.matchDate(java.time.LocalDateTime.of(2026, 8, 13, 8, 0))
				.build();
	}
}
