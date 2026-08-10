package com.toy.nar.app.mobile.push;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MobileDevicePlatform;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.PlayerSoloRankPushDeliveryRepository;
import com.toy.nar.domain.participant.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerSoloRankPushServiceTest {

	@Mock
	private MemberDeviceRepository deviceRepository;

	@Mock
	private PlayerSoloRankPushDeliveryRepository deliveryRepository;

	@Mock
	private MobilePushGateway pushGateway;

	@Mock
	private MemberNotificationService notificationService;

	private PlayerSoloRankPushService service;

	@BeforeEach
	void setUp() {
		service = new PlayerSoloRankPushService(
				deviceRepository, deliveryRepository, pushGateway, notificationService);
		when(pushGateway.isAvailable()).thenReturn(true);
	}

	@Test
	void sendsOncePerSubscribedMemberAndDeactivatesInvalidTokens() {
		Player player = player(10L, "Faker");
		MemberDevice first = device(1L, member(7L), "token-1");
		MemberDevice second = device(2L, member(8L), "token-2");

		when(deviceRepository.findActiveDevicesBySubscribedPlayerId(10L))
				.thenReturn(List.of(first, second));
		when(deliveryRepository.reserveAll(any(), eq(10L), eq("game-1"))).thenReturn(List.of(7L, 8L));
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(1, 1, List.of("token-2"), List.of("token-1")));

		service.notifySubscribers(player, "game-1", "아리", "ahri.png", "솔로 랭크", "https://www.op.gg/summoners/kr/Faker-KR1");

		verify(deliveryRepository).markSentAll(List.of(7L), 10L, "game-1");
		verify(deliveryRepository).markFailedAll(List.of(8L), 10L, "game-1", "FCM 전송 성공 기기가 없습니다.");
		verify(deviceRepository).deactivateByFcmTokenIn(List.of("token-2"));
	}

	@Test
	void skipsAlreadyReservedDelivery() {
		Player player = player(10L, "Faker");
		MemberDevice device = device(1L, member(7L), "token");
		when(deviceRepository.findActiveDevicesBySubscribedPlayerId(10L)).thenReturn(List.of(device));
		when(deliveryRepository.reserveAll(any(), eq(10L), eq("game-1"))).thenReturn(List.of());

		service.notifySubscribers(player, "game-1", "아리", "ahri.png", "솔로 랭크", "https://www.op.gg/summoners/kr/Faker-KR1");

		verify(pushGateway, never()).send(any(), any());
	}

	@Test
	void pushFailureDoesNotEscapeMonitorFlow() {
		Player player = player(10L, "Faker");
		MemberDevice device = device(1L, member(7L), "token");
		when(deviceRepository.findActiveDevicesBySubscribedPlayerId(10L)).thenReturn(List.of(device));
		when(deliveryRepository.reserveAll(any(), eq(10L), eq("game-1"))).thenReturn(List.of(7L));
		when(pushGateway.send(any(), any())).thenThrow(new IllegalStateException("firebase down"));

		assertThatCode(() -> service.notifySubscribers(player, "game-1", "아리", "ahri.png", "솔로 랭크", "https://www.op.gg/summoners/kr/Faker-KR1"))
				.doesNotThrowAnyException();
		verify(deliveryRepository).markFailedAll(List.of(7L), 10L, "game-1", "firebase down");
	}

	private Member member(Long id) {
		Member member = Member.builder().name("member-" + id).tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Player player(Long id, String name) {
		Player player = Player.builder().name(name).imageUrl("faker.png").build();
		ReflectionTestUtils.setField(player, "id", id);
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
