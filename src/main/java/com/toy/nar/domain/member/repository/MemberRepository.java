package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNameAndTag(String name, String tag);
    Optional<Member> findByEmail(String email);
    // 이메일 자동 연동용. 혹시 중복 이메일이 있어도 가장 오래된 계정으로 안전하게 연결한다.
    Optional<Member> findFirstByEmailOrderByIdAsc(String email);

    // 백오피스 검색: 닉네임(name)·이메일 부분일치. q 가 null 이면 전체.
    @Query("""
            SELECT m FROM Member m
            WHERE :q IS NULL
               OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Member> searchForBackoffice(@Param("q") String q, Pageable pageable);
}
