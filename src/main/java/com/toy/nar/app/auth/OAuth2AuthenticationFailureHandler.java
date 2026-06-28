package com.toy.nar.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mobile-redirect-url:nar://oauth/callback}")
    private String mobileRedirectUrl;

    @Value("${app.backoffice-url:http://localhost:5173}")
    private String backofficeUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.error("[OAuth2] 인증 실패: {}", exception.getMessage(), exception);
        String callbackUrl = resolveCallbackUrl(request, response);
        String redirectUrl = UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam("error", "oauth_failed")
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String resolveCallbackUrl(HttpServletRequest request, HttpServletResponse response) {
        String target = authorizationRequestRepository.removeRedirectTarget(request, response);
        if ("mobile".equals(target)) {
            return mobileRedirectUrl;
        }
        if ("backoffice".equals(target)) {
            return backofficeUrl + "/login";
        }
        return frontendUrl + "/oauth/callback";
    }
}
