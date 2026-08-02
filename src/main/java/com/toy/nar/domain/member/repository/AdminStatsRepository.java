package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백오피스 대시보드 집계 전용 리포지토리.
 *
 * <p>회원·구독·알림·평점 네 도메인을 가로지르는 집계라 엔티티별 리포지토리에 흩어놓지 않고 여기 모았다.
 * 루트 엔티티는 {@link Member} 지만 실제 쿼리는 전부 네이티브라 테이블을 직접 읽는다.
 *
 * <p>시간 버킷은 <b>1시간</b> 단위로만 내려준다. 일별 화면은 프론트에서 시간 버킷을 합쳐 쓴다
 * (일별/24시간 뷰가 쿼리 하나를 공유하고, 24시간 롤링 윈도가 자정을 가로질러도 그대로 만들어진다).
 * 값이 0인 버킷은 행 자체가 없다 — 빈 구간 채우기는 화면 쪽 책임.
 */
public interface AdminStatsRepository extends Repository<Member, Long> {

    /** {@code bucket} = {@code 2026-08-02T13:00} 형식(서버 로컬시각), {@code cnt} = 그 1시간 동안의 건수. */
    interface HourCount {
        String getBucket();

        long getCnt();
    }

    interface SubscriptionHourCount {
        String getBucket();

        long getPlayerCnt();

        long getTeamCnt();

        long getMatchCnt();
    }

    interface LabelCount {
        String getLabel();

        long getCnt();
    }

    interface MemberTotals {
        long getTotalMembers();

        long getOnboardedMembers();

        long getSubscribedMembers();

        long getRatedMembers();
    }

    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:00') AS bucket, COUNT(*) AS cnt
            FROM member
            WHERE created_at >= :from
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<HourCount> signupsByHour(@Param("from") LocalDateTime from);

    /**
     * 선수·팀·경기 구독 세 테이블을 한 쿼리로. 각 테이블에서 먼저 시간별로 접은 뒤 UNION 하므로
     * 스캔은 테이블당 한 번, UNION 에 올라오는 행은 최대 (구간 시간 수 × 3) 개다.
     */
    @Query(value = """
            SELECT bucket,
                   SUM(p) AS playerCnt,
                   SUM(t) AS teamCnt,
                   SUM(m) AS matchCnt
            FROM (
                SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:00') AS bucket, COUNT(*) AS p, 0 AS t, 0 AS m
                FROM member_favorite_player WHERE created_at >= :from GROUP BY bucket
                UNION ALL
                SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:00'), 0, COUNT(*), 0
                FROM member_team_notification_subscription WHERE created_at >= :from GROUP BY 1
                UNION ALL
                SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:00'), 0, 0, COUNT(*)
                FROM member_match_subscription WHERE created_at >= :from GROUP BY 1
            ) x
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<SubscriptionHourCount> subscriptionsByHour(@Param("from") LocalDateTime from);

    /** 인앱 알림 생성 건수 = 발송량. 푸시 전송 결과 테이블(delivery)이 아니라 알림함 기준이다. */
    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:00') AS bucket, COUNT(*) AS cnt
            FROM member_notification
            WHERE created_at >= :from
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<HourCount> notificationsByHour(@Param("from") LocalDateTime from);

    /** 퍼널 4단계(가입 → 온보딩 → 구독 → 평점)를 한 방에. 각 단계는 회원 수(중복 없음)다. */
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM member) AS totalMembers,
                   (SELECT COUNT(*) FROM member WHERE onboarded_at IS NOT NULL) AS onboardedMembers,
                   (SELECT COUNT(*) FROM (
                        SELECT member_id FROM member_favorite_player
                        UNION
                        SELECT member_id FROM member_team_notification_subscription
                        UNION
                        SELECT member_id FROM member_match_subscription) s) AS subscribedMembers,
                   (SELECT COUNT(DISTINCT member_id) FROM live_player_rating) AS ratedMembers
            FROM dual
            """, nativeQuery = true)
    MemberTotals memberTotals();

    @Query(value = """
            SELECT COALESCE(favorite_league_name, '미설정') AS label, COUNT(*) AS cnt
            FROM member
            GROUP BY label
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<LabelCount> membersByFavoriteLeague();

    @Query(value = """
            SELECT t.team_name AS label, COUNT(*) AS cnt
            FROM member_team_notification_subscription s
            JOIN teams t ON t.team_id = s.team_id
            GROUP BY t.team_id, t.team_name
            ORDER BY cnt DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LabelCount> topSubscribedTeams(@Param("limit") int limit);

    @Query(value = """
            SELECT p.player_name AS label, COUNT(*) AS cnt
            FROM member_favorite_player f
            JOIN players p ON p.player_id = f.player_id
            GROUP BY p.player_id, p.player_name
            ORDER BY cnt DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LabelCount> topSubscribedPlayers(@Param("limit") int limit);
}
