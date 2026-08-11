package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByMemberId(Long memberId);

    /** 세션 상한 판정용. expiresAt = 발급+14일 고정이라 만료 내림차순 = 발급 최신순. */
    java.util.List<RefreshToken> findByMemberIdOrderByExpiresAtDesc(Long memberId);

    /**
     * 토큰 회전용 벌크 삭제. 엔티티 삭제(delete(stored))는 동시 리프레시 두 건이 같은 행을
     * 지우면 늦은 쪽이 row count 0 flush 예외로 500 이 난다(#321 탈퇴와 같은 계급).
     * 벌크 삭제는 0건도 정상이라 늦은 쪽도 자기 토큰으로 커밋된다.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RefreshToken rt where rt.token = :token")
    int deleteByToken(@org.springframework.data.repository.query.Param("token") String token);

    /**
     * 회전 grace: 구 토큰을 즉시 지우는 대신 만료를 단축한다. 즉시 삭제하면 동시 리프레시의
     * 늦은 쪽이 findByToken 에서 401 을 받아 클라이언트가 강제 로그아웃된다(실측 2026-08-11
     * 커넥션 풀 대기로 레이스 창 5초). 조건의 {@code > :graceEnd} 는 단축만 허용 — 이미 grace 에
     * 들어간 토큰을 뒤이은 재사용이 다시 연장하지 못한다.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update RefreshToken rt set rt.expiresAt = :graceEnd where rt.token = :token and rt.expiresAt > :graceEnd")
    int shortenExpiryByToken(
            @org.springframework.data.repository.query.Param("token") String token,
            @org.springframework.data.repository.query.Param("graceEnd") java.time.LocalDateTime graceEnd);

    /** grace 방식이 남기는 짧은 수명 행이 쌓이지 않게 회전·로그인 시 만료분을 함께 지운다. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "delete from RefreshToken rt where rt.member.id = :memberId and rt.expiresAt < :now")
    int deleteExpiredByMemberId(
            @org.springframework.data.repository.query.Param("memberId") Long memberId,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
