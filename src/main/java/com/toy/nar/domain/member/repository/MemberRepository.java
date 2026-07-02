package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNameAndTag(String name, String tag);
    Optional<Member> findByEmail(String email);
    // 이메일 자동 연동용. 혹시 중복 이메일이 있어도 가장 오래된 계정으로 안전하게 연결한다.
    Optional<Member> findFirstByEmailOrderByIdAsc(String email);
}
