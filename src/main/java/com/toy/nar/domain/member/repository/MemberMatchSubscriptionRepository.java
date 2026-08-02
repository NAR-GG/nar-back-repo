package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MemberMatchSubscriptionRepository
		extends JpaRepository<MemberMatchSubscription, Long> {

	boolean existsByMemberIdAndMatchId(Long memberId, String matchId);

	void deleteByMemberIdAndMatchId(Long memberId, String matchId);

	@Query("""
			SELECT subscription.matchId
			FROM MemberMatchSubscription subscription
			WHERE subscription.member.id = :memberId
			""")
	List<String> findMatchIdsByMemberId(@Param("memberId") Long memberId);

	// 백오피스 구독 탭: 구독자가 1명 이상인 경기만(인기순, 동수면 최신 경기 우선).
	// 전체 경기는 대부분 구독자 0이라 INNER JOIN 으로 걸러낸다. q 는 경기명·팀명 검색.
	// match_date 는 UTC 저장이라 KST 변환은 컨트롤러에서 한다(리뷰 탭과 동일).
	@Query(value = """
			SELECT lm.id AS matchId,
			       lm.league_name AS leagueName,
			       lm.match_title AS matchTitle,
			       lm.blue_team_name AS blueTeamName,
			       lm.red_team_name AS redTeamName,
			       lm.state AS state,
			       lm.match_date AS matchDate,
			       COUNT(*) AS subscriberCount
			FROM member_match_subscription mms
			JOIN league_match lm ON lm.id = mms.match_id
			WHERE (:q IS NULL
			       OR LOWER(lm.match_title) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(lm.blue_team_name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(lm.red_team_name) LIKE LOWER(CONCAT('%', :q, '%')))
			GROUP BY lm.id, lm.league_name, lm.match_title, lm.blue_team_name,
			         lm.red_team_name, lm.state, lm.match_date
			ORDER BY subscriberCount DESC, lm.match_date DESC
			""",
			countQuery = """
			SELECT COUNT(DISTINCT mms.match_id)
			FROM member_match_subscription mms
			JOIN league_match lm ON lm.id = mms.match_id
			WHERE (:q IS NULL
			       OR LOWER(lm.match_title) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(lm.blue_team_name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(lm.red_team_name) LIKE LOWER(CONCAT('%', :q, '%')))
			""",
			nativeQuery = true)
	Page<SubscribedMatchView> findSubscribedMatches(@Param("q") String q, Pageable pageable);

	// 백오피스 구독 탭: 특정 경기를 구독한 회원 목록(최근 구독순) + 알림 토글 상태. 팀 구독과 동일 패턴.
	@Query(value = """
			SELECT m.id AS memberId,
			       m.name AS name,
			       m.tag AS tag,
			       m.email AS email,
			       mms.set_start_enabled AS setStartEnabled,
			       mms.set_end_enabled AS setEndEnabled,
			       mms.live_event_enabled AS liveEventEnabled,
			       mms.created_at AS subscribedAt
			FROM member_match_subscription mms
			JOIN member m ON m.id = mms.member_id
			WHERE mms.match_id = :matchId
			  AND (:q IS NULL
			       OR (:field = 'memberId' AND CAST(m.id AS CHAR) LIKE CONCAT('%', :q, '%'))
			       OR (:field = 'email' AND LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'nickname' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))))
			ORDER BY mms.created_at DESC
			""",
			countQuery = """
			SELECT COUNT(*)
			FROM member_match_subscription mms
			JOIN member m ON m.id = mms.member_id
			WHERE mms.match_id = :matchId
			  AND (:q IS NULL
			       OR (:field = 'memberId' AND CAST(m.id AS CHAR) LIKE CONCAT('%', :q, '%'))
			       OR (:field = 'email' AND LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'nickname' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))))
			""",
			nativeQuery = true)
	Page<MatchSubscriberView> findSubscribersByMatchId(@Param("matchId") String matchId,
			@Param("field") String field, @Param("q") String q, Pageable pageable);

	interface SubscribedMatchView {
		String getMatchId();

		String getLeagueName();

		String getMatchTitle();

		String getBlueTeamName();

		String getRedTeamName();

		String getState();

		LocalDateTime getMatchDate();

		long getSubscriberCount();
	}

	interface MatchSubscriberView {
		Long getMemberId();

		String getName();

		String getTag();

		String getEmail();

		Boolean getSetStartEnabled();

		Boolean getSetEndEnabled();

		Boolean getLiveEventEnabled();

		LocalDateTime getSubscribedAt();
	}
}
