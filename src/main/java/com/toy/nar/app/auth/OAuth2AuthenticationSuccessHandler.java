package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mobile-redirect-url:nar://oauth/callback}")
    private String mobileRedirectUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long memberId = ((Number) oAuth2User.getAttribute("memberId")).longValue();
        boolean isOnboarded = Boolean.TRUE.equals(oAuth2User.getAttribute("isOnboarded"));

        Member member = memberRepository.findById(memberId).orElseThrow();
        String accessToken = jwtTokenProvider.createAccessToken(memberId, isOnboarded);
        String refreshTokenValue = jwtTokenProvider.createRefreshToken(memberId);

        refreshTokenRepository.deleteByMemberId(memberId);
        refreshTokenRepository.save(RefreshToken.builder()
                .member(member)
                .token(refreshTokenValue)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
                .build());

        String callbackUrl = resolveCallbackUrl(request, response);
        String redirectUrl = UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshTokenValue)
                .queryParam("isOnboarded", isOnboarded)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String resolveCallbackUrl(HttpServletRequest request, HttpServletResponse response) {
        String target = authorizationRequestRepository.removeRedirectTarget(request, response);
        if ("mobile".equals(target)) {
            return mobileRedirectUrl;
        }
        return frontendUrl + "/oauth/callback";
    }
}
