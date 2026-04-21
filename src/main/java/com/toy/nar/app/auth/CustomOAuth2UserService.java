package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;
    private final MemberSocialRepository memberSocialRepository;
    private final NicknameGenerator nicknameGenerator;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        Member member = memberSocialRepository
                .findByProviderAndProviderId(attrs.getProvider(), attrs.getProviderId())
                .map(MemberSocial::getMember)
                .orElseGet(() -> createMember(attrs));

        Map<String, Object> principalAttrs = Map.of(
                "memberId", member.getId(),
                "isOnboarded", member.isOnboarded()
        );
        return new DefaultOAuth2User(Collections.emptyList(), principalAttrs, "memberId");
    }

    private Member createMember(OAuthAttributes attrs) {
        Member member = memberRepository.save(
                Member.builder()
                        .nickname(nicknameGenerator.generate())
                        .email(attrs.getEmail())
                        .build()
        );
        memberSocialRepository.save(
                MemberSocial.builder()
                        .member(member)
                        .provider(attrs.getProvider())
                        .providerId(attrs.getProviderId())
                        .build()
        );
        return member;
    }
}
