package com.toy.nar.app.auth.profile;

import com.toy.nar.api.auth.dto.MemberResponse;
import com.toy.nar.app.auth.profile.dto.ProfileUpdateRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.service.FavoriteTeamChangePolicy;
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
	private final FavoriteTeamChangePolicy favoriteTeamChangePolicy;

	@Transactional
	public MemberResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"));

		String name = request.name().trim();
		String tag = request.tag().trim();
		// 본인의 현재 이름#태그 조합과 동일하면 중복검사 통과, 조합이 바뀌는 경우에만 중복 확인
		boolean unchanged = name.equals(member.getName()) && tag.equals(member.getTag());
		if (!unchanged && memberRepository.existsByNameAndTag(name, tag)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다");
		}

		Team team = teamRepository.findById(request.favoriteTeamId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다"));

		// 응원팀이 실제로 바뀌는 요청이면 30일 쿨다운을 여기서 막는다. 앱도 완료 버튼에서
		// 먼저 막지만, API 직접 호출로 팀만 갈아타는 경로가 남으면 제한이 아니다.
		favoriteTeamChangePolicy.checkChangeable(member, team);

		member.updateProfile(name, tag, team, request.profileImageUrl());
		return MemberResponse.from(member, favoriteTeamChangePolicy.changeAvailableFrom(member));
	}
}
