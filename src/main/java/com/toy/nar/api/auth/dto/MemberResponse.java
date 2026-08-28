package com.toy.nar.api.auth.dto;

import com.toy.nar.app.image.CloudinaryUrls;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "로그인 사용자 정보 응답")
public record MemberResponse(
		@Schema(description = "회원 ID", example = "12")
		Long id,
		@Schema(description = "이름#태그 합성 닉네임 (표시용)", example = "짱아깨비#KR2")
		String nickname,
		@Schema(description = "이름", example = "짱아깨비")
		String name,
		@Schema(description = "태그", example = "KR2")
		String tag,
		@Schema(description = "소셜 로그인 이메일. 동의하지 않은 경우 null", example = "user@example.com", nullable = true)
		String email,
		@Schema(description = "선호 리그명", example = "LCK", nullable = true)
		String favoriteLeagueName,
		@Schema(description = "선호 팀 ID", example = "3", nullable = true)
		Long favoriteTeamId,
		@Schema(description = "선호 선수 ID 목록", example = "[10, 11]")
		List<Long> favoritePlayerIds,
		@Schema(description = "프로필 이미지 URL", example = "https://storage.example.com/profiles/12.png", nullable = true)
		String profileImageUrl,
		@Schema(description = "온보딩 완료 여부", example = "false")
		boolean isOnboarded,
		@Schema(description = "응원팀을 다시 바꿀 수 있는 시각. null 이면 지금 바꿀 수 있다.",
				example = "2026-09-28T01:00:00", nullable = true)
		LocalDateTime favoriteTeamChangeAvailableFrom) {

    /** 쿨다운을 모르는 자리(로그인 응답 등)에서 쓴다 — 팀 변경 화면은 아래 오버로드를 쓸 것. */
    public static MemberResponse from(Member member) {
        return from(member, null);
    }

    public static MemberResponse from(Member member, LocalDateTime favoriteTeamChangeAvailableFrom) {
        return new MemberResponse(
                member.getId(),
                member.getNickname(),
                member.getName(),
                member.getTag(),
                member.getEmail(),
                member.getFavoriteLeagueName(),
                member.getFavoriteTeam() != null ? member.getFavoriteTeam().getId() : null,
                member.getFavoritePlayers().stream()
                        .map(MemberFavoritePlayer::getPlayer)
                        .map(player -> player.getId())
                        .toList(),
                CloudinaryUrls.with(member.getProfileImageUrl(), CloudinaryUrls.AVATAR),
                member.isOnboarded(),
                favoriteTeamChangeAvailableFrom
        );
    }
}
