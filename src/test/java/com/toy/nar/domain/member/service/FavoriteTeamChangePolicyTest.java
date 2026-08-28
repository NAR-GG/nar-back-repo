package com.toy.nar.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.common.error.exception.CommunityWriteBlockedException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Team;

/** 응원팀 변경 쿨다운 — 팀 갈아타기를 30일에 한 번으로 묶는다. */
class FavoriteTeamChangePolicyTest {

	private final FavoriteTeamChangePolicy policy = new FavoriteTeamChangePolicy();

	private static Team team(long id) {
		Team team = new Team("팀" + id, "T" + id, null);
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}

	private static Member memberWithTeam(long teamId) {
		Member member = Member.builder().name("이름").tag("0001").build();
		member.updateProfile("이름", "0001", team(teamId), null); // 최초 선택 — 스탬프 없음
		return member;
	}

	@Test
	void 최초_선택은_변경이_아니라_쿨다운이_없다() {
		Member member = memberWithTeam(1L);
		assertThat(policy.changeAvailableFrom(member)).isNull();
		assertThatCode(() -> policy.checkChangeable(member, team(2L))).doesNotThrowAnyException();
	}

	@Test
	void 바꾼_직후엔_다시_못_바꾼다() {
		Member member = memberWithTeam(1L);
		policy.checkChangeable(member, team(2L));
		member.updateProfile("이름", "0001", team(2L), null); // 실제 변경 — 스탬프

		assertThat(policy.changeAvailableFrom(member)).isAfter(LocalDateTime.now().plusDays(29));
		assertThatThrownBy(() -> policy.checkChangeable(member, team(3L)))
				.isInstanceOf(CommunityWriteBlockedException.class);
	}

	@Test
	void 쿨다운_중이어도_같은_팀_재선택은_통과한다() {
		// 이름·사진만 고치는 저장이 팀 필드 때문에 막히면 안 된다.
		Member member = memberWithTeam(1L);
		member.updateProfile("이름", "0001", team(2L), null);

		assertThatCode(() -> policy.checkChangeable(member, team(2L))).doesNotThrowAnyException();
	}

	@Test
	void 쿨다운_0일이면_제한이_꺼진다() {
		// prod 킬 스위치(COMMUNITY_TEAM_CHANGE_COOLDOWN_DAYS=0). 제한을 설명하는 앱이
		// 스토어에 나가기 전까지는 이 값으로 꺼 둔다 — 켜면 구버전 앱 사용자가 이유
		// 없는 "저장 실패" 만 보게 된다.
		FavoriteTeamChangePolicy off = new FavoriteTeamChangePolicy();
		ReflectionTestUtils.setField(off, "cooldownDays", 0L);

		Member member = memberWithTeam(1L);
		member.updateProfile("이름", "0001", team(2L), null); // 방금 바꿈

		assertThat(off.changeAvailableFrom(member)).isNull();
		assertThatCode(() -> off.checkChangeable(member, team(3L))).doesNotThrowAnyException();
	}

	@Test
	void 서른날이_지나면_다시_바꿀_수_있다() {
		Member member = memberWithTeam(1L);
		member.updateProfile("이름", "0001", team(2L), null);
		ReflectionTestUtils.setField(member, "favoriteTeamChangedAt", LocalDateTime.now().minusDays(31));

		assertThat(policy.changeAvailableFrom(member)).isNull();
		assertThatCode(() -> policy.checkChangeable(member, team(3L))).doesNotThrowAnyException();
	}
}
