package com.toy.nar.app.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.repository.CommunityCommentRepository;
import com.toy.nar.domain.community.repository.CommunityPostRepository;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Team;

/** 게시판 쓰기 자격 판정 — 잠금 바(boardViewer)와 쓰기 403 이 같은 로직을 탄다. */
class CommunityWriteGuardTest {

	private final CommunityWriteGuard guard = new CommunityWriteGuard(
			mock(CommunityPostRepository.class), mock(CommunityCommentRepository.class));

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
	void 팀을_바꿔도_응원팀_게시판이면_바로_쓸_수_있다() {
		// 팀 변경 30일 쿨다운은 쓰기가 아니라 변경 시점에서 막는다
		// (FavoriteTeamChangePolicy). 여기서 또 막으면 이미 바꾼 사람이
		// 자기 팀 게시판에 30일간 못 들어간다.
		Member member = memberWithTeam(1L);
		Team newTeam = new Team("팀2", "T2", null);
		ReflectionTestUtils.setField(newTeam, "id", 2L);
		member.updateProfile("이름", "0001", newTeam, null); // 실제 변경 — 스탬프

		var result = guard.evaluateBoardWritability(member, 2L);
		assertThat(result.canWrite()).isTrue();
		assertThat(result.reason()).isNull();
	}
}

