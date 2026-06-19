package com.toy.nar.app.mobile.device;

import com.toy.nar.app.mobile.device.dto.MobileDeviceRegistrationRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MobileDevicePlatform;
import com.toy.nar.domain.member.repository.MemberDeviceRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileDeviceServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberDeviceRepository deviceRepository;

	private MobileDeviceService service;

	@BeforeEach
	void setUp() {
		service = new MobileDeviceService(memberRepository, deviceRepository);
	}

	@Test
	void registersNewDeviceAndReactivatesExistingToken() {
		Member member = member(7L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(deviceRepository.findByFcmToken("token"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(MemberDevice.builder()
						.member(member)
						.fcmToken("token")
						.platform(MobileDevicePlatform.ANDROID)
						.build()));
		when(deviceRepository.save(any(MemberDevice.class)))
				.thenAnswer(invocation -> {
					MemberDevice device = invocation.getArgument(0);
					ReflectionTestUtils.setField(device, "id", 3L);
					return device;
				});

		var created = service.register(
				7L,
				new MobileDeviceRegistrationRequest("token", MobileDevicePlatform.ANDROID));
		var updated = service.register(
				7L,
				new MobileDeviceRegistrationRequest("token", MobileDevicePlatform.IOS));

		assertThat(created.deviceId()).isEqualTo(3L);
		assertThat(updated.platform()).isEqualTo(MobileDevicePlatform.IOS);
		assertThat(updated.active()).isTrue();
	}

	@Test
	void deactivatesOnlyOwnedDevice() {
		MemberDevice device = MemberDevice.builder()
				.member(member(7L))
				.fcmToken("token")
				.platform(MobileDevicePlatform.ANDROID)
				.build();
		when(deviceRepository.findByIdAndMember_Id(3L, 7L)).thenReturn(Optional.of(device));

		service.deactivate(7L, 3L);

		assertThat(device.isActive()).isFalse();
	}

	@Test
	void rejectsUnauthenticatedRegistration() {
		assertThatThrownBy(() -> service.register(
				null,
				new MobileDeviceRegistrationRequest("token", MobileDevicePlatform.ANDROID)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("로그인");
	}

	private Member member(Long id) {
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
