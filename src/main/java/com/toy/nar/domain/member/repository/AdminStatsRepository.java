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
 *
 * <p>버킷 키는 <b>1970-01-01 부터 흐른 시간(hour)</b> 정수다. 문자열({@code DATE_FORMAT})로 그룹핑하면 35만 행짜리
 * 알림 집계가 프로덕션에서 3,311ms 걸렸고, 같은 쿼리를 정수 키로 바꾸니 1,257ms 였다(2026-08-03 EXPLAIN ANALYZE
 * 실측). 사람이 읽을 포맷 변환은 서비스에서 한다.
 *
 * <p><b>{@code UNIX_TIMESTAMP()} 를 쓰지 않는 이유</b>: {@code created_at} 은 타임존 없는 {@code DATETIME}(=KST 벽시계)
 * 인데 {@code UNIX_TIMESTAMP()} 는 그 값을 <b>MySQL 세션 {@code time_zone} 기준</b>으로 해석한다. JDBC 의
 * {@code serverTimezone}/{@code connectionTimeZone} 은 클라이언트 변환 옵션일 뿐 세션 타임존을 바꾸지 않으므로,
 * DB 서버가 UTC 면 버킷이 9시간 밀린다(프로덕션에서 실제로 밀렸다). {@code TIMESTAMPDIFF} 는 벽시계끼리의
 * 순수 산술이라 세션 타임존과 무관하고, 문자열 시절 {@code DATE_FORMAT} 과 의미가 정확히 같다.
 */
public interface AdminStatsRepository extends Repository<Member, Long> {

    /** {@code bucket} = 1970-01-01 00:00 부터 흐른 시간(벽시계 기준), {@code cnt} = 그 1시간 동안의 건수. */
    interface HourCount {
        long getBucket();

        long getCnt();
    }

    interface SubscriptionHourCount {
        long getBucket();

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
            SELECT TIMESTAMPDIFF(HOUR, '1970-01-01', created_at) AS bucket, COUNT(*) AS cnt
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
                SELECT TIMESTAMPDIFF(HOUR, '1970-01-01', created_at) AS bucket, COUNT(*) AS p, 0 AS t, 0 AS m
                FROM member_favorite_player WHERE created_at >= :from GROUP BY bucket
                UNION ALL
                SELECT TIMESTAMPDIFF(HOUR, '1970-01-01', created_at), 0, COUNT(*), 0
                FROM member_team_notification_subscription WHERE created_at >= :from GROUP BY 1
                UNION ALL
                SELECT TIMESTAMPDIFF(HOUR, '1970-01-01', created_at), 0, 0, COUNT(*)
                FROM member_match_subscription WHERE created_at >= :from GROUP BY 1
            ) x
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<SubscriptionHourCount> subscriptionsByHour(@Param("from") LocalDateTime from);

    /**
     * 인앱 알림 생성 건수 = 발송량. 푸시 전송 결과 테이블(delivery)이 아니라 알림함 기준이다.
     *
     * <p>대시보드에서 제일 비싼 쿼리. 알림은 대량 발송이라 35만 행이 최근 몇 주에 몰려 있어
     * 기간을 좁혀도 스캔량이 줄지 않고(30일·60일 모두 전량), 모든 행이 조건에 걸려 인덱스 seek 도 의미가 없다.
     * 그래서 별도 엔드포인트 + 캐시로 분리해 나머지 카드가 먼저 그려지게 한다.
     */
    @Query(value = """
            SELECT TIMESTAMPDIFF(HOUR, '1970-01-01', created_at) AS bucket, COUNT(*) AS cnt
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
