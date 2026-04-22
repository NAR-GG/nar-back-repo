package com.toy.nar.api.auth.dto;

import com.toy.nar.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 사용자 정보 응답")
public record MemberResponse(
		@Schema(description = "회원 ID", example = "12")
		Long id,
		@Schema(description = "랜덤 생성 또는 사용 중인 닉네임", example = "용맹한바론")
		String nickname,
		@Schema(description = "선호 팀 ID", example = "3", nullable = true)
		Long favoriteTeamId,
		@Schema(description = "온보딩 완료 여부", example = "false")
		boolean isOnboarded) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getNickname(),
                member.getFavoriteTeam() != null ? member.getFavoriteTeam().getId() : null,
                member.isOnboarded()
        );
    }
}
