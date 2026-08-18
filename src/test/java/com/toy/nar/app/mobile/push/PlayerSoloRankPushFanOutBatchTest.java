package com.toy.nar.app.mobile.push;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MobileDevicePlatform;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.PlayerSoloRankPushDeliveryRepository;
import com.toy.nar.domain.participant.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 솔랭 푸시 팬아웃이 발송·DB 왕복을 구독자 수와 무관한 상수로 묶는지 지키는 회귀 테스트.
 *
 * <p>구독자마다 {@code pushGateway.send} 와 {@code reserve}/{@code markSent} 를 한 건씩 돌던 탓에
 * 팬아웃이 폴 스레드를 분 단위로 점유했다. 실측 2026-08-04 프로덕션: Oner 구독자 1,502명 팬아웃
 * 472초(1명당 0.31초), 그 사이 100계정 폴링이 완전히 멈춰 22:27→22:41 동안 새 게임을 못 봤다.
 * 같은 문제를 팀 라이브 이벤트 경로에서 먼저 고쳤고(#323·#325), 이 테스트는 솔랭 경로가
 * 그 상태로 되돌아가지 않게 막는다.</p>
 */
class PlayerSoloRankPushFanOutBatchTest {

	private static final Long PLAYER_ID = 10L;
	private static final String GAME_ID = "game-1";
	private static final String OPGG_URL = "https://www.op.gg/summoners/kr/Faker-KR1";

	private final MemberDeviceRepository deviceRepository = mock(MemberDeviceRepository.class);
	private final PlayerSoloRankPushDeliveryRepository deliveryRepository =
			mock(PlayerSoloRankPushDeliveryRepository.class);
	private final MobilePushGateway pushGateway = mock(MobilePushGateway.class);
	private final MemberNotificationService notificationService = mock(MemberNotificationService.class);
	private final QuietAwarePushSender quietAwarePushSender = mock(QuietAwarePushSender.class);

	private PlayerSoloRankPushService service;

	@BeforeEach
	void setUp() {
		service = new PlayerSoloRankPushService(
				deviceRepository, deliveryRepository, pushGateway, notificationService, quietAwarePushSender);
		when(pushGateway.isAvailable()).thenReturn(true);
	}

	@Test
	@DisplayName("잠자기로 건너뛴 구독자는 알림함에만 남고 SKIPPED_QUIET 로 마감된다")
	void 잠자기_구독자는_알림함에만_남는다() {
		givenSubscribers(device(1L, member(7L), "token-1"), device(2L, member(8L), "token-2"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START")))
				.thenReturn(List.of(7L, 8L));
		// 8번 회원은 잠자기라 발송에서 빠졌다.
		when(quietAwarePushSender.send(any(), any())).thenReturn(new QuietAwarePushSender.Outcome(
				new MobilePushResult(1, 0, List.of(), List.of("token-1")), List.of(8L)));

		notifyGame();

		verify(deliveryRepository).markSentAll(List.of(7L), PLAYER_ID, GAME_ID, "START");
		verify(deliveryRepository).markSkippedQuietAll(List.of(8L), PLAYER_ID, GAME_ID, "START");
		// FAILED 로 남으면 재예약돼서 잠자기가 끝난 뒤 뒤늦은 푸시가 나간다.
		verify(deliveryRepository, never()).markFailedAll(any(), any(), any(), any(), any());
		// 알림함에는 발송 성공 회원과 건너뛴 회원이 모두 남아야 한다(발송 전에 예약자 전원으로 기록).
		ArgumentCaptor<Collection<Long>> feed = ArgumentCaptor.forClass(Collection.class);
		verify(notificationService).recordAll(feed.capture(), any(), anyString(), any(), any());
		assertThat(feed.getValue()).containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	@DisplayName("구독자 3명이어도 발송 1회·예약 1회·마감 1회로 끝낸다")
	void 구독자_수와_무관하게_호출_횟수가_상수다() {
		givenSubscribers(device(1L, member(7L), "token-1"),
				device(2L, member(8L), "token-2"),
				device(3L, member(9L), "token-3"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START")))
				.thenReturn(List.of(7L, 8L, 9L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(3, 0, List.of(), List.of("token-1", "token-2", "token-3")), List.of()));

		notifyGame();

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender, times(1)).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-1", "token-2", "token-3");
		verify(pushGateway, never()).send(any(), any());

		verify(deliveryRepository, times(1)).reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"));
		verify(deliveryRepository, times(1)).markSentAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"));
		verify(notificationService, times(1)).recordAll(any(), any(), anyString(), any(), any());
	}

	@Test
	@DisplayName("토큰별 결과로 구독자를 갈라 마감한다")
	void 성공한_토큰의_구독자만_발송_기록한다() {
		givenSubscribers(device(1L, member(7L), "token-1"), device(2L, member(8L), "token-2"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"))).thenReturn(List.of(7L, 8L));
		// token-2 만 성공
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(1, 1, List.of(), List.of("token-2")), List.of()));

		notifyGame();

		assertThat(capturedIds(sentCaptor())).containsExactly(8L);
		assertThat(capturedIds(failedCaptor())).containsExactly(7L);
		// 알림함은 발송 전에 예약자 전원으로 남긴다 — FCM 실패자(7번)도 포함된다.
		// 발송 결과를 기다리면 푸시를 받고 바로 마이구독을 열었을 때 비어 있다.
		ArgumentCaptor<Collection<Long>> recorded = ArgumentCaptor.forClass(Collection.class);
		verify(notificationService).recordAll(recorded.capture(), any(), anyString(), any(), any());
		assertThat(recorded.getValue()).containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	@DisplayName("예약에서 빠진 구독자는 토큰에 포함하지 않는다")
	void 예약되지_않은_구독자는_발송_대상에서_빠진다() {
		givenSubscribers(device(1L, member(7L), "token-1"), device(2L, member(8L), "token-2"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"))).thenReturn(List.of(8L));
		when(quietAwarePushSender.send(any(), any()))
				.thenReturn(new QuietAwarePushSender.Outcome(new MobilePushResult(1, 0, List.of(), List.of("token-2")), List.of()));

		notifyGame();

		ArgumentCaptor<Map<Long, List<String>>> byMember = ArgumentCaptor.forClass(Map.class);
		verify(quietAwarePushSender).send(byMember.capture(), any());
		assertThat(byMember.getValue().values().stream().flatMap(List::stream).toList())
				.containsExactly("token-2");
	}

	@Test
	@DisplayName("예약 대상이 없으면 발송하지 않는다")
	void 예약_대상이_없으면_발송하지_않는다() {
		givenSubscribers(device(1L, member(7L), "token-1"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"))).thenReturn(List.of());

		notifyGame();

		verify(quietAwarePushSender, never()).send(any(), any());
	}

	@Test
	@DisplayName("발송 자체가 실패하면 예약한 구독자 전원을 FAILED 로 남긴다")
	void 발송_예외시_예약자_전원을_실패로_남긴다() {
		givenSubscribers(device(1L, member(7L), "token-1"), device(2L, member(8L), "token-2"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"))).thenReturn(List.of(7L, 8L));
		when(quietAwarePushSender.send(any(), any())).thenThrow(new IllegalStateException("firebase down"));

		notifyGame();

		ArgumentCaptor<Collection<Long>> failed = ArgumentCaptor.forClass(Collection.class);
		verify(deliveryRepository)
				.markFailedAll(failed.capture(), eq(PLAYER_ID), eq(GAME_ID), eq("START"), eq("firebase down"));
		assertThat(failed.getValue()).containsExactly(7L, 8L);
		verify(deliveryRepository, never()).markSentAll(any(), anyLong(), anyString(), anyString());
		// FCM 이 죽어도 알림함에는 남는다 — 앱을 열면 무슨 알림이 있었는지는 보여야 한다.
		verify(notificationService).recordAll(any(), any(), anyString(), any(), any());
	}

	@Test
	@DisplayName("알림함 기록이 FCM 발송보다 먼저 일어난다")
	void 피드를_발송_전에_남긴다() {
		givenSubscribers(device(1L, member(7L), "token-1"));
		when(deliveryRepository.reserveAll(any(), eq(PLAYER_ID), eq(GAME_ID), eq("START"))).thenReturn(List.of(7L));
		when(quietAwarePushSender.send(any(), any())).thenReturn(new QuietAwarePushSender.Outcome(
				new MobilePushResult(1, 0, List.of(), List.of("token-1")), List.of()));

		notifyGame();

		// 발송 뒤에 기록하면 푸시를 받고 바로 마이구독을 열었을 때 그 알림이 없다.
		// 실측 2026-08-11(Zeus 구독 1,440명): 기기 수신 → 피드 INSERT 까지 약 29초.
		InOrder order = inOrder(notificationService, quietAwarePushSender);
		order.verify(notificationService).recordAll(any(), any(), anyString(), any(), any());
		order.verify(quietAwarePushSender).send(any(), any());
	}

	private void notifyGame() {
		service.notifySubscribers(player(), GAME_ID, "아리", "ahri.png", "솔로 랭크", OPGG_URL);
	}

	private void givenSubscribers(MemberDevice... devices) {
		when(deviceRepository.findActiveDevicesBySubscribedPlayerId(PLAYER_ID, "START")).thenReturn(List.of(devices));
	}

	private ArgumentCaptor<Collection<Long>> sentCaptor() {
		ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(deliveryRepository).markSentAll(captor.capture(), eq(PLAYER_ID), eq(GAME_ID), eq("START"));
		return captor;
	}

	private ArgumentCaptor<Collection<Long>> failedCaptor() {
		ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(deliveryRepository).markFailedAll(captor.capture(), eq(PLAYER_ID), eq(GAME_ID), eq("START"), anyString());
		return captor;
	}

	private List<Long> capturedIds(ArgumentCaptor<Collection<Long>> captor) {
		return List.copyOf(captor.getValue());
	}

	private Member member(Long id) {
		Member member = Member.builder().name("member-" + id).tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Player player() {
		Player player = Player.builder().name("Faker").imageUrl("faker.png").build();
		ReflectionTestUtils.setField(player, "id", PLAYER_ID);
		return player;
	}

	private MemberDevice device(Long id, Member member, String token) {
		MemberDevice device = MemberDevice.builder()
				.member(member)
				.fcmToken(token)
				.platform(MobileDevicePlatform.ANDROID)
				.build();
		ReflectionTestUtils.setField(device, "id", id);
		return device;
	}
}
