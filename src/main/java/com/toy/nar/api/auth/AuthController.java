package com.toy.nar.api.auth;

import com.toy.nar.api.auth.dto.MemberResponse;
import com.toy.nar.api.auth.dto.OnboardingRequest;
import com.toy.nar.api.auth.dto.TokenResponse;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TeamRepository teamRepository;

    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<TokenResponse> refresh(@RequestParam String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "유효하지 않은 리프레시 토큰"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(UNAUTHORIZED, "만료된 리프레시 토큰");
        }

        Member member = stored.getMember();
        String newAccessToken = jwtTokenProvider.createAccessToken(member.getId(), member.isOnboarded());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        refreshTokenRepository.delete(stored);
        refreshTokenRepository.save(RefreshToken.builder()
                .member(member)
                .token(newRefreshToken)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
                .build());

        return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken, member.isOnboarded()));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long memberId) {
        if (memberId != null) {
            refreshTokenRepository.deleteByMemberId(memberId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/onboarding")
    @Transactional
    public ResponseEntity<MemberResponse> onboarding(@AuthenticationPrincipal Long memberId,
                                                      @Valid @RequestBody OnboardingRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "회원을 찾을 수 없습니다"));
        Team team = teamRepository.findById(request.favoriteTeamId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "팀을 찾을 수 없습니다"));

        member.completeOnboarding(team);
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> me(@AuthenticationPrincipal Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "회원을 찾을 수 없습니다"));
        return ResponseEntity.ok(MemberResponse.from(member));
    }
}
