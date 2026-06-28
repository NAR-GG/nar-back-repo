package com.toy.nar.app.auth;

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

    private final SocialLoginService socialLoginService;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mobile-redirect-url:nar://oauth/callback}")
    private String mobileRedirectUrl;

    @Value("${app.backoffice-url:http://localhost:5173}")
    private String backofficeUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long memberId = ((Number) oAuth2User.getAttribute("memberId")).longValue();
        AuthTokens tokens = socialLoginService.issueTokens(memberId);

        String callbackUrl = resolveCallbackUrl(request, response);
        String redirectUrl = UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam("accessToken", tokens.accessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .queryParam("isOnboarded", tokens.isOnboarded())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String resolveCallbackUrl(HttpServletRequest request, HttpServletResponse response) {
        String target = authorizationRequestRepository.removeRedirectTarget(request, response);
        if ("mobile".equals(target)) {
            return mobileRedirectUrl;
        }
        if ("backoffice".equals(target)) {
            return backofficeUrl + "/oauth/callback";
        }
        return frontendUrl + "/oauth/callback";
    }
}
