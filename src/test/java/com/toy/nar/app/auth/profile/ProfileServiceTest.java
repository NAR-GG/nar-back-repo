package com.toy.nar.app.auth.profile;

import com.toy.nar.api.auth.dto.MemberResponse;
import com.toy.nar.app.auth.profile.dto.ProfileUpdateRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

	private MemberRepository memberRepository;
	private TeamRepository teamRepository;
	private ProfileService profileService;

	@BeforeEach
	void setUp() {
		memberRepository = mock(MemberRepository.class);
		teamRepository = mock(TeamRepository.class);
		profileService = new ProfileService(memberRepository, teamRepository);
	}

	private Member member(Long id, String nickname) {
		Member member = Member.builder().nickname(nickname).email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Team team(Long id) {
		Team team = Team.builder().name("T1").code("T1").imageUrl("t1.png").build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}

	@Test
	void updatesProfileSuccessfully() {
		Member member = member(7L, "옛닉네임");
		Team team = team(3L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNickname("새닉네임")).thenReturn(false);
		when(teamRepository.findById(3L)).thenReturn(Optional.of(team));

		MemberResponse response = profileService.updateProfile(
				7L, new ProfileUpdateRequest("새닉네임", 3L, "https://img/p.png"));

		assertThat(response.nickname()).isEqualTo("새닉네임");
		assertThat(response.favoriteTeamId()).isEqualTo(3L);
		assertThat(response.profileImageUrl()).isEqualTo("https://img/p.png");
		assertThat(member.getNickname()).isEqualTo("새닉네임");
		assertThat(member.getFavoriteTeam()).isSameAs(team);
		assertThat(member.getProfileImageUrl()).isEqualTo("https://img/p.png");
	}

	@Test
	void failsWhenNicknameTakenByAnotherMember() {
		Member member = member(7L, "옛닉네임");
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNickname("중복닉네임")).thenReturn(true);

		assertThatThrownBy(() -> profileService.updateProfile(
				7L, new ProfileUpdateRequest("중복닉네임", 3L, null)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("이미 사용 중인 닉네임");
	}

	@Test
	void passesWhenNicknameUnchanged() {
		Member member = member(7L, "내닉네임");
		Team team = team(3L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(teamRepository.findById(3L)).thenReturn(Optional.of(team));

		MemberResponse response = profileService.updateProfile(
				7L, new ProfileUpdateRequest("내닉네임", 3L, null));

		assertThat(response.nickname()).isEqualTo("내닉네임");
		// 본인 현재 닉네임과 동일하면 중복검사를 호출하지 않는다
		verify(memberRepository, never()).existsByNickname(anyString());
	}

	@Test
	void failsWhenFavoriteTeamNotFound() {
		Member member = member(7L, "내닉네임");
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNickname("새닉네임")).thenReturn(false);
		when(teamRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.updateProfile(
				7L, new ProfileUpdateRequest("새닉네임", 999L, null)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("팀을 찾을 수 없습니다");
	}
}
