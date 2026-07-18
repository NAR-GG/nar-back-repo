package com.toy.nar.app.mobile.push;

import com.toy.nar.app.lolesports.WorldsService;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberTeamEventPushDeliveryRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SET_END 푸시 스코어 라인 검증.
 * 세트 N 종료 시점의 스코어 합은 반드시 N — DB가 stale(합 < N)이면
 * 업스트림(getEventDetails)을 직접 조회하고, 그래도 stale이면 틀린 스코어 대신 생략한다.
 */
@ExtendWith(MockitoExtension.class)
class TeamLiveEventPushServiceScoreLineTest {

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

	private TeamLiveEventPushService service;

	@BeforeEach
	void setUp() {
		service = new TeamLiveEventPushService(
				deviceRepository, deliveryRepository, teamExternalIdentityRepository,
				leagueMatchRepository, pushGateway, notificationService, worldsService);
		// 테스트에서 재시도 대기 없이 즉시 진행
		ReflectionTestUtils.setField(service, "scoreRetryAttempts", 3);
		ReflectionTestUtils.setField(service, "scoreRetryDelayMs", 0L);
	}

	private LeagueMatch match(Integer blueScore, Integer redScore) {
		return LeagueMatch.builder()
				.id("m1")
				.leagueName("EWC")
				.blueTeamName("Karmine Corp")
				.redTeamName("T1")
				.blueScore(blueScore)
				.redScore(redScore)
				.build();
	}

	@Test
	void DB_스코어_합이_세트수와_일치하면_그대로_쓴다() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(0, 1)));

		String line = service.buildMatchScoreLine("m1", 1);

		assertThat(line).isEqualTo("Karmine Corp 0 vs 1 T1");
		verify(worldsService, never()).fetchMatchGameWins("m1");
	}

	@Test
	void DB가_stale이면_업스트림_스코어로_대체한다() {
		// 2세트 종료인데 DB는 아직 1세트까지만 반영(0:1)
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(0, 1)));
		when(worldsService.fetchMatchGameWins("m1")).thenReturn(new int[] { 0, 2 });

		String line = service.buildMatchScoreLine("m1", 2);

		assertThat(line).isEqualTo("Karmine Corp 0 vs 2 T1");
	}

	@Test
	void 업스트림도_계속_stale이면_스코어를_생략한다() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(0, 1)));
		when(worldsService.fetchMatchGameWins("m1")).thenReturn(new int[] { 0, 1 });

		String line = service.buildMatchScoreLine("m1", 2);

		assertThat(line).isNull();
	}

	@Test
	void 업스트림_조회가_실패하면_스코어를_생략한다() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(0, 1)));
		when(worldsService.fetchMatchGameWins("m1")).thenReturn(null);

		String line = service.buildMatchScoreLine("m1", 2);

		assertThat(line).isNull();
	}

	@Test
	void 세트번호를_모르면_기존처럼_합이_0보다_큰_DB_스코어를_쓴다() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.of(match(0, 1)));

		String line = service.buildMatchScoreLine("m1", 0);

		assertThat(line).isEqualTo("Karmine Corp 0 vs 1 T1");
		verify(worldsService, never()).fetchMatchGameWins("m1");
	}

	@Test
	void 매치가_없으면_null() {
		when(leagueMatchRepository.findById("m1")).thenReturn(Optional.empty());

		assertThat(service.buildMatchScoreLine("m1", 2)).isNull();
	}
}
