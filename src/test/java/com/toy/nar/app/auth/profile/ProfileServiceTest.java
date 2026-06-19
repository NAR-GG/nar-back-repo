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

	private Member member(Long id, String name, String tag) {
		Member member = Member.builder().name(name).tag(tag).email("test@example.com").build();
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
		Member member = member(7L, "옛이름", "OLD1");
		Team team = team(3L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNameAndTag("짱아깨비", "KR2")).thenReturn(false);
		when(teamRepository.findById(3L)).thenReturn(Optional.of(team));

		MemberResponse response = profileService.updateProfile(
				7L, new ProfileUpdateRequest("짱아깨비", "KR2", 3L, "https://img/p.png"));

		assertThat(response.name()).isEqualTo("짱아깨비");
		assertThat(response.tag()).isEqualTo("KR2");
		assertThat(response.nickname()).isEqualTo("짱아깨비#KR2");
		assertThat(response.favoriteTeamId()).isEqualTo(3L);
		assertThat(response.profileImageUrl()).isEqualTo("https://img/p.png");
		assertThat(member.getName()).isEqualTo("짱아깨비");
		assertThat(member.getTag()).isEqualTo("KR2");
		assertThat(member.getFavoriteTeam()).isSameAs(team);
	}

	@Test
	void changesTagOnlyKeepingName() {
		// 롤처럼 이름은 그대로, 태그만 #OLD1 -> #KR2 로 변경
		Member member = member(7L, "짱아깨비", "OLD1");
		Team team = team(3L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNameAndTag("짱아깨비", "KR2")).thenReturn(false);
		when(teamRepository.findById(3L)).thenReturn(Optional.of(team));

		MemberResponse response = profileService.updateProfile(
				7L, new ProfileUpdateRequest("짱아깨비", "KR2", 3L, null));

		assertThat(response.nickname()).isEqualTo("짱아깨비#KR2");
		assertThat(member.getTag()).isEqualTo("KR2");
		// 조합이 바뀌었으므로 중복검사를 호출해야 한다
		verify(memberRepository).existsByNameAndTag("짱아깨비", "KR2");
	}

	@Test
	void failsWhenNameTagTakenByAnotherMember() {
		Member member = member(7L, "옛이름", "OLD1");
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNameAndTag("짱아깨비", "KR1")).thenReturn(true);

		assertThatThrownBy(() -> profileService.updateProfile(
				7L, new ProfileUpdateRequest("짱아깨비", "KR1", 3L, null)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("이미 사용 중인");
	}

	@Test
	void passesWhenNameAndTagUnchanged() {
		Member member = member(7L, "짱아깨비", "KR1");
		Team team = team(3L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(teamRepository.findById(3L)).thenReturn(Optional.of(team));

		MemberResponse response = profileService.updateProfile(
				7L, new ProfileUpdateRequest("짱아깨비", "KR1", 3L, null));

		assertThat(response.nickname()).isEqualTo("짱아깨비#KR1");
		// 본인 현재 이름#태그와 동일하면 중복검사를 호출하지 않는다
		verify(memberRepository, never()).existsByNameAndTag(anyString(), anyString());
	}

	@Test
	void failsWhenFavoriteTeamNotFound() {
		Member member = member(7L, "내이름", "KR1");
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(memberRepository.existsByNameAndTag("새이름", "KR9")).thenReturn(false);
		when(teamRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.updateProfile(
				7L, new ProfileUpdateRequest("새이름", "KR9", 999L, null)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("팀을 찾을 수 없습니다");
	}
}
