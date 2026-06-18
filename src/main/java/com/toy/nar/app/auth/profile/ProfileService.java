package com.toy.nar.app.auth.profile;

import com.toy.nar.api.auth.dto.MemberResponse;
import com.toy.nar.app.auth.profile.dto.ProfileUpdateRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;

	@Transactional
	public MemberResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"));

		String nickname = request.nickname().trim();
		// 본인의 현재 닉네임과 동일하면 중복검사 통과, 변경되는 경우에만 중복 확인
		if (!nickname.equals(member.getNickname()) && memberRepository.existsByNickname(nickname)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다");
		}

		Team team = teamRepository.findById(request.favoriteTeamId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다"));

		member.updateProfile(nickname, team, request.profileImageUrl());
		return MemberResponse.from(member);
	}
}
