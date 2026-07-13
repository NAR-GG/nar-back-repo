package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberSocialRepository extends JpaRepository<MemberSocial, Long> {
    Optional<MemberSocial> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    void deleteByMemberId(Long memberId);
}
