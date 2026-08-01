package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberSocialRepository extends JpaRepository<MemberSocial, Long> {
    Optional<MemberSocial> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    /**
     * 회원의 소셜 연동을 일괄 삭제한다.
     *
     * <p>파생 삭제(deleteByMemberId)는 엔티티를 조회한 뒤 한 건씩 삭제해서, 동시 요청이 같은 행을
     * 지우면 "Batch update returned unexpected row count from update [0]" 로 500 이 났다.
     * 탈퇴 응답이 늦어 사용자가 버튼을 연타하면 실제로 발생한다(2026-07-29 프로덕션 확인).
     * 벌크 삭제는 0건이어도 정상이라 멱등하다.
     */
    @Modifying
    @Query("delete from MemberSocial social where social.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
