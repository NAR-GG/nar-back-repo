package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import com.toy.nar.domain.member.repository.MemberMatchSubscriptionRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileMatchSubscriptionServiceTest {

	@Mock
	private MemberMatchSubscriptionRepository subscriptionRepository;
	@Mock
	private MemberRepository memberRepository;
	@Mock
	private LeagueMatchRepository leagueMatchRepository;

	private MobileMatchSubscriptionService service;

	@BeforeEach
	void setUp() {
		service = new MobileMatchSubscriptionService(
				subscriptionRepository, memberRepository, leagueMatchRepository);
	}

	@Test
	void subscribeSavesNewSubscription() {
		when(leagueMatchRepository.existsById("m1")).thenReturn(true);
		when(subscriptionRepository.existsByMemberIdAndMatchId(7L, "m1")).thenReturn(false);
		when(memberRepository.findById(7L)).thenReturn(
				Optional.of(Member.builder().name("nar").tag("0001").build()));

		service.subscribe(7L, "m1", true, true, true);

		verify(subscriptionRepository).save(any(MemberMatchSubscription.class));
	}

	@Test
	void subscribeIsIdempotentWhenAlreadySubscribed() {
		when(leagueMatchRepository.existsById("m1")).thenReturn(true);
		when(subscriptionRepository.existsByMemberIdAndMatchId(7L, "m1")).thenReturn(true);

		service.subscribe(7L, "m1", true, true, true);

		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void subscribeRejectsUnknownMatch() {
		when(leagueMatchRepository.existsById("nope")).thenReturn(false);

		assertThatThrownBy(() -> service.subscribe(7L, "nope", true, true, true))
				.isInstanceOf(CustomException.class);
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void unsubscribeDelegatesToRepository() {
		service.unsubscribe(7L, "m1");

		verify(subscriptionRepository).deleteByMemberIdAndMatchId(7L, "m1");
	}
}
