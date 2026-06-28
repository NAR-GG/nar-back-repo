package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SocialLoginService socialLoginService;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        SocialAccountInfo info = new SocialAccountInfo(attrs.getProvider(), attrs.getProviderId(), attrs.getEmail());
        // 백오피스 로그인은 기존 ADMIN 회원만 허용(회원 생성 안 함). 일반 로그인은 없으면 생성.
        Member member = isBackofficeLogin()
                ? socialLoginService.findAdminMember(info)
                : socialLoginService.findOrCreateMember(info);

        Map<String, Object> principalAttrs = Map.of(
                "memberId", member.getId(),
                "isOnboarded", member.isOnboarded()
        );
        return new DefaultOAuth2User(Collections.emptyList(), principalAttrs, "memberId");
    }

    private boolean isBackofficeLogin() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest request = sra.getRequest();
            return authorizationRequestRepository.isBackofficeLogin(request);
        }
        return false;
    }
}
