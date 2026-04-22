package com.toy.nar.app.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Optional<Cookie> cookie = CookieUtils.getCookie(request, COOKIE_NAME);
        log.debug("[OAuth2Cookie] load - cookie present={}, uri={}", cookie.isPresent(), request.getRequestURI());
        return cookie.map(c -> CookieUtils.deserialize(c, OAuth2AuthorizationRequest.class)).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            CookieUtils.deleteCookie(request, response, COOKIE_NAME);
            log.debug("[OAuth2Cookie] save - deleted cookie");
            return;
        }
        log.debug("[OAuth2Cookie] save - setting cookie, state={}", authorizationRequest.getState());
        CookieUtils.addCookie(response, COOKIE_NAME, CookieUtils.serialize(authorizationRequest), COOKIE_MAX_AGE);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest request0 = loadAuthorizationRequest(request);
        CookieUtils.deleteCookie(request, response, COOKIE_NAME);
        return request0;
    }
}
