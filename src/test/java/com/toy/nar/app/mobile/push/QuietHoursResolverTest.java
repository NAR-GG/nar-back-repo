package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.dto.MemberQuietHours;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuietHoursResolverTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Mock
	private MemberRepository memberRepository;

	/** KST 로 주어진 시각에 고정된 시계. */
	private static Clock fixedAt(int hour, int minute) {
		return Clock.fixed(
				LocalTime.of(hour, minute).atDate(java.time.LocalDate.of(2026, 8, 10)).atZone(KST).toInstant(),
				KST);
	}

	@Test
	void 같은_날_구간은_시작은_포함하고_종료는_제외한다() {
		LocalTime start = LocalTime.of(1, 0);
		LocalTime end = LocalTime.of(8, 0);

		assertThat(QuietHoursResolver.isWithin(LocalTime.of(0, 59), start, end)).isFalse();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(1, 0), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(7, 59), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(8, 0), start, end)).isFalse();
	}

	@Test
	void 자정을_넘는_구간도_판정한다() {
		LocalTime start = LocalTime.of(23, 0);
		LocalTime end = LocalTime.of(8, 0);

		assertThat(QuietHoursResolver.isWithin(LocalTime.of(23, 30), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(0, 30), start, end)).isTrue();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(12, 0), start, end)).isFalse();
		assertThat(QuietHoursResolver.isWithin(LocalTime.of(22, 59), start, end)).isFalse();
	}

	@Test
	void 구간에_든_회원만_돌려준다() {
		when(memberRepository.findQuietHoursByMemberIds(Set.of(1L, 2L))).thenReturn(List.of(
				new MemberQuietHours(1L, true, LocalTime.of(1, 0), LocalTime.of(8, 0)),
				new MemberQuietHours(2L, true, LocalTime.of(13, 0), LocalTime.of(14, 0))));
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of(1L, 2L))).containsExactlyInAnyOrder(1L);
	}

	@Test
	void 빈_입력이면_쿼리하지_않는다() {
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of())).isEmpty();
		verifyNoInteractions(memberRepository);
	}

	@Test
	void 조회가_실패하면_아무도_잠자기로_보지_않는다() {
		when(memberRepository.findQuietHoursByMemberIds(any()))
				.thenThrow(new RuntimeException("db down"));
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, fixedAt(2, 30));

		assertThat(resolver.quietMemberIds(Set.of(1L))).isEmpty();
	}

	@Test
	void 시계_존이_KST_가_아니어도_KST_로_판정한다() {
		// 2026-08-10T18:00Z = 2026-08-11T03:00 KST. 잠자기 01:00~08:00 안이다.
		// resolver 가 KST 로 강제하지 않고 시계 존(UTC)을 그대로 쓰면 18:00 이라 구간 밖이 된다.
		Clock utcClock = Clock.fixed(Instant.parse("2026-08-10T18:00:00Z"), ZoneOffset.UTC);
		when(memberRepository.findQuietHoursByMemberIds(Set.of(1L))).thenReturn(List.of(
				new MemberQuietHours(1L, true, LocalTime.of(1, 0), LocalTime.of(8, 0))));
		QuietHoursResolver resolver = new QuietHoursResolver(memberRepository, utcClock);

		assertThat(resolver.quietMemberIds(Set.of(1L))).containsExactlyInAnyOrder(1L);
	}
}
