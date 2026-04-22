package com.toy.nar.api.auth.dto;

import com.toy.nar.domain.member.entity.Member;

public record MemberResponse(Long id, String nickname, Long favoriteTeamId, boolean isOnboarded) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getNickname(),
                member.getFavoriteTeam() != null ? member.getFavoriteTeam().getId() : null,
                member.isOnboarded()
        );
    }
}
