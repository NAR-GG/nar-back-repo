package com.toy.nar.app.mobile.notification;

import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴한 회원의 액세스 토큰은 만료(최대 30분)까지 유효하다.
 * 예전엔 이 API 만 200 + 빈 목록을 줘서(다른 me/** 는 404) 앱이 로그인 상태로 오인했다.
 */
class MemberNotificationServiceWithdrawnMemberTest {

	private final MemberNotificationRepository notificationRepository = mock(MemberNotificationRepository.class);
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final MemberNotificationService service =
			new MemberNotificationService(notificationRepository, memberRepository);

	@Test
	@DisplayName("탈퇴한 회원의 토큰으로 알림 목록을 조회하면 401이고 조회 쿼리도 타지 않는다")
	void withdrawnMemberGetsUnauthorized() {
		when(memberRepository.existsById(7L)).thenReturn(false);

		assertThatThrownBy(() -> service.getNotifications(7L, null, null, 0, 20))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

		verify(notificationRepository, never())
				.findByMember_IdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong(),
						org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymousGetsUnauthorized() {
		assertThatThrownBy(() -> service.getNotifications(null, null, null, 0, 20))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
	}
}
