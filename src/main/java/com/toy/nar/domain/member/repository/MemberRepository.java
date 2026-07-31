package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNameAndTag(String name, String tag);
    Optional<Member> findByEmail(String email);
    // 이메일 자동 연동용. 혹시 중복 이메일이 있어도 가장 오래된 계정으로 안전하게 연결한다.
    Optional<Member> findFirstByEmailOrderByIdAsc(String email);

    /**
     * 탈퇴용 회원 삭제. 삭제된 행 수를 반환하며 0(이미 탈퇴)도 정상이다.
     *
     * <p>엔티티 삭제(delete(member))는 동시 요청이 같은 행을 지우면 stale state 로 500 이 난다.
     * 벌크 삭제는 그 경우 0을 반환해 멱등하다. 자식 데이터는 DB FK ON DELETE CASCADE 로 함께 지워진다
     * (벌크 삭제라 JPA 캐스케이드는 타지 않지만, 삭제 규칙은 DB 레벨이라 그대로 적용된다).
     */
    @Modifying
    @Query("delete from Member member where member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);

    // 백오피스 검색: 닉네임(name)·이메일 부분일치. q 가 null 이면 전체.
    @Query("""
            SELECT m FROM Member m
            WHERE :q IS NULL
               OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Member> searchForBackoffice(@Param("q") String q, Pageable pageable);
}
