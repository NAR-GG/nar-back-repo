package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileQuietHoursServiceTest {

	@Mock
	private MemberRepository memberRepository;

	private MobileQuietHoursService service;
	private Member member;

	@BeforeEach
	void setUp() {
		service = new MobileQuietHoursService(memberRepository);
		member = Member.builder().name("테스터").tag("0001").build();
	}

	@Test
	void 잠자기_설정을_저장한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		QuietHoursResponse response = service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(23, 30), LocalTime.of(8, 0)));

		assertThat(response.enabled()).isTrue();
		assertThat(response.startTime()).isEqualTo(LocalTime.of(23, 30));
		assertThat(response.endTime()).isEqualTo(LocalTime.of(8, 0));
		assertThat(member.isQuietHoursEnabled()).isTrue();
	}

	@Test
	void 시작과_종료가_같으면_거부한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(1, 0), LocalTime.of(1, 0))))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUIET_HOURS);
	}

	@Test
	void 분이_5의_배수가_아니면_거부한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(1, 3), LocalTime.of(8, 0))))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUIET_HOURS);
	}

	@Test
	void 종료_분이_5의_배수가_아니면_거부한다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.update(
				1L, new QuietHoursUpdateRequest(true, LocalTime.of(1, 0), LocalTime.of(8, 7))))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUIET_HOURS);
	}

	@Test
	void 꺼진_상태로_저장할_때는_시각을_검증하지_않는다() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		QuietHoursResponse response = service.update(
				1L, new QuietHoursUpdateRequest(false, LocalTime.of(1, 0), LocalTime.of(1, 0)));

		assertThat(response.enabled()).isFalse();
	}
}
