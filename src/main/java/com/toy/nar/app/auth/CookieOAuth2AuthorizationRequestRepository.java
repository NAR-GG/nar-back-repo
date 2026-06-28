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
    private static final String REDIRECT_TARGET_COOKIE_NAME = "oauth2_redirect_target";
    private static final int COOKIE_MAX_AGE = 180;
    private static final String MOBILE_TARGET = "mobile";
    private static final String BACKOFFICE_TARGET = "backoffice";

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
            CookieUtils.deleteCookie(request, response, REDIRECT_TARGET_COOKIE_NAME);
            log.debug("[OAuth2Cookie] save - deleted cookie");
            return;
        }
        log.debug("[OAuth2Cookie] save - setting cookie, state={}", authorizationRequest.getState());
        CookieUtils.addCookie(response, COOKIE_NAME, CookieUtils.serialize(authorizationRequest), COOKIE_MAX_AGE);
        saveRedirectTarget(request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest request0 = loadAuthorizationRequest(request);
        CookieUtils.deleteCookie(request, response, COOKIE_NAME);
        return request0;
    }

    /** 현재 요청이 백오피스 로그인 흐름인지(쿠키만 확인, 제거하지 않음). */
    public boolean isBackofficeLogin(HttpServletRequest request) {
        return CookieUtils.getCookie(request, REDIRECT_TARGET_COOKIE_NAME)
                .map(Cookie::getValue)
                .filter(BACKOFFICE_TARGET::equals)
                .isPresent();
    }

    public String removeRedirectTarget(HttpServletRequest request, HttpServletResponse response) {
        String target = CookieUtils.getCookie(request, REDIRECT_TARGET_COOKIE_NAME)
                .map(Cookie::getValue)
                .orElse(null);
        CookieUtils.deleteCookie(request, response, REDIRECT_TARGET_COOKIE_NAME);
        return target;
    }

    private void saveRedirectTarget(HttpServletRequest request, HttpServletResponse response) {
        String target = request.getParameter("target");
        if (MOBILE_TARGET.equalsIgnoreCase(target) || BACKOFFICE_TARGET.equalsIgnoreCase(target)) {
            CookieUtils.addCookie(response, REDIRECT_TARGET_COOKIE_NAME,
                    target.toLowerCase(), COOKIE_MAX_AGE);
            return;
        }
        CookieUtils.deleteCookie(request, response, REDIRECT_TARGET_COOKIE_NAME);
    }
}
