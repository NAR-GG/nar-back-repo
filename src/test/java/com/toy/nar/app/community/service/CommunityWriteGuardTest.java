package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.common.error.exception.CommunityWriteBlockedException;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.repository.CommunityCommentRepository;
import com.toy.nar.domain.community.repository.CommunityPostRepository;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Team;

/** 게시판 쓰기 자격 판정 — 잠금 바(boardViewer)와 쓰기 403 이 같은 로직을 탄다. */
class CommunityWriteGuardTest {

	private final CommunityWriteGuard guard = new CommunityWriteGuard(
			mock(CommunityPostRepository.class), mock(CommunityCommentRepository.class));

	CommunityWriteGuardTest() {
		ReflectionTestUtils.setField(guard, "teamChangeCooldownDays", 30L);
	}

	private static Member memberWithTeam(long teamId) {
		Team team = new Team("팀" + teamId, "T" + teamId, null);
		ReflectionTestUtils.setField(team, "id", teamId);
		Member member = Member.builder().name("이름").tag("0001").build();
		member.updateProfile("이름", "0001", team, null); // 최초 선택 — 쿨다운 없음
		return member;
	}

	@Test
	void 전체게시판은_항상_쓰기_가능() {
		var result = guard.evaluateBoardWritability(memberWithTeam(1L), null);
		assertThat(result.canWrite()).isTrue();
	}

	@Test
	void 응원팀이_아니면_NOT_FAN() {
		var result = guard.evaluateBoardWritability(memberWithTeam(1L), 2L);
		assertThat(result.canWrite()).isFalse();
		assertThat(result.reason()).isEqualTo("NOT_FAN");
		assertThatThrownBy(() -> guard.checkBoardWritable(memberWithTeam(1L), 2L))
				.isInstanceOf(CustomException.class);
	}

	@Test
	void 팀변경_30일_안이면_COOLDOWN_과_writableFrom() {
		Member member = memberWithTeam(1L);
		Team newTeam = new Team("팀2", "T2", null);
		ReflectionTestUtils.setField(newTeam, "id", 2L);
		member.updateProfile("이름", "0001", newTeam, null); // 실제 변경 — 스탬프

		var result = guard.evaluateBoardWritability(member, 2L);
		assertThat(result.canWrite()).isFalse();
		assertThat(result.reason()).isEqualTo("COOLDOWN");
		assertThat(result.writableFrom()).isAfter(LocalDateTime.now().plusDays(29));
		assertThatThrownBy(() -> guard.checkBoardWritable(member, 2L))
				.isInstanceOf(CommunityWriteBlockedException.class);
	}

	@Test
	void 쿨다운이_지나면_쓰기_가능() {
		Member member = memberWithTeam(1L);
		Team newTeam = new Team("팀2", "T2", null);
		ReflectionTestUtils.setField(newTeam, "id", 2L);
		member.updateProfile("이름", "0001", newTeam, null);
		ReflectionTestUtils.setField(member, "favoriteTeamChangedAt", LocalDateTime.now().minusDays(31));

		assertThat(guard.evaluateBoardWritability(member, 2L).canWrite()).isTrue();
	}
}
