package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.OAuthProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public class OAuthAttributes {

    private final OAuthProvider provider;
    private final String providerId;
    private final String email;

    @SuppressWarnings("unchecked")
    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> ofGoogle(attributes);
            case "kakao" -> ofKakao(attributes);
            case "naver" -> ofNaver(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth 프로바이더: " + registrationId);
        };
    }

    private static OAuthAttributes ofGoogle(Map<String, Object> attrs) {
        return new OAuthAttributes(
                OAuthProvider.GOOGLE,
                (String) attrs.get("sub"),
                (String) attrs.get("email")
        );
    }

    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofKakao(Map<String, Object> attrs) {
        String providerId = String.valueOf(attrs.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) attrs.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        return new OAuthAttributes(OAuthProvider.KAKAO, providerId, email);
    }

    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofNaver(Map<String, Object> attrs) {
        Map<String, Object> response = (Map<String, Object>) attrs.get("response");
        return new OAuthAttributes(
                OAuthProvider.NAVER,
                (String) response.get("id"),
                (String) response.get("email")
        );
    }
}
