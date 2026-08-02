package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // 백오피스 상세: 관심 팀 이름을 같이 쓰므로 fetch 해둔다(OSIV off).
    @EntityGraph(attributePaths = "favoriteTeam")
    Optional<Member> findWithFavoriteTeamById(Long id);

    /**
     * 백오피스 검색: 닉네임(name)·이메일 부분일치. q 가 null 이면 전체.
     * 구독 수 두 개는 스칼라 서브쿼리 — 각각 (member_id, …) 유니크 인덱스 선두 컬럼을 탄다.
     *
     * <p>파생 테이블로 한 겹 감싸는 이유: Spring 이 정렬을 붙일 때 별칭 앞에 테이블 별칭을 박는다
     * ({@code order by m.favoritePlayerCount}). 서브쿼리 별칭은 바깥에서 그렇게 참조할 수 없어
     * {@code Unknown column} 500 이 났다(실측). 감싸면 전부 파생 테이블 m 의 진짜 컬럼이 된다.
     * 정렬 프로퍼티는 그대로 SQL 로 나가므로 컨트롤러에서 화이트리스트로 거른다.
     */
    @Query(value = """
            SELECT *
            FROM (SELECT m.id AS id,
                         m.name AS name,
                         m.tag AS tag,
                         m.email AS email,
                         m.favorite_league_name AS favoriteLeagueName,
                         m.created_at AS createdAt,
                         (SELECT COUNT(*) FROM member_favorite_player f WHERE f.member_id = m.id) AS favoritePlayerCount,
                         (SELECT COUNT(*) FROM member_team_notification_subscription s WHERE s.member_id = m.id) AS favoriteTeamCount
                  FROM member m
                  WHERE :q IS NULL
                     OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                     OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%'))) m
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM member m
            WHERE :q IS NULL
               OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
            nativeQuery = true)
    Page<BackofficeMemberView> searchForBackoffice(@Param("q") String q, Pageable pageable);

    interface BackofficeMemberView {
        Long getId();

        String getName();

        String getTag();

        String getEmail();

        String getFavoriteLeagueName();

        LocalDateTime getCreatedAt();

        long getFavoritePlayerCount();

        long getFavoriteTeamCount();
    }
}
