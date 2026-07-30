package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByMemberId(Long memberId);

    /**
     * 토큰 회전용 벌크 삭제. 엔티티 삭제(delete(stored))는 동시 리프레시 두 건이 같은 행을
     * 지우면 늦은 쪽이 row count 0 flush 예외로 500 이 난다(#321 탈퇴와 같은 계급).
     * 벌크 삭제는 0건도 정상이라 늦은 쪽도 자기 토큰으로 커밋된다.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RefreshToken rt where rt.token = :token")
    int deleteByToken(@org.springframework.data.repository.query.Param("token") String token);
}
