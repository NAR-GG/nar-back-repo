package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberTeamNotificationSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberTeamNotificationSubscriptionRepository
		extends JpaRepository<MemberTeamNotificationSubscription, Long> {

	@EntityGraph(attributePaths = "team")
	List<MemberTeamNotificationSubscription> findByMember_Id(Long memberId);

	// 백오피스 회원 상세: 최근 구독순.
	@EntityGraph(attributePaths = "team")
	List<MemberTeamNotificationSubscription> findByMember_IdOrderByCreatedAtDesc(Long memberId);

	@EntityGraph(attributePaths = "team")
	Optional<MemberTeamNotificationSubscription> findByMember_IdAndTeam_Id(Long memberId, Long teamId);

	// 백오피스 구독 탭: 특정 팀을 구독한 회원 목록(최근 구독순) + 알림 토글 상태. 페이징.
	// field(memberId|nickname|email) + q 로 검색. 닉네임은 화면 표기와 동일한 "name#tag" 기준.
	@Query(value = """
			SELECT m.id AS memberId,
			       m.name AS name,
			       m.tag AS tag,
			       m.email AS email,
			       mtns.set_start_enabled AS setStartEnabled,
			       mtns.set_end_enabled AS setEndEnabled,
			       mtns.live_event_enabled AS liveEventEnabled,
			       mtns.created_at AS subscribedAt
			FROM member_team_notification_subscription mtns
			JOIN member m ON m.id = mtns.member_id
			WHERE mtns.team_id = :teamId
			  AND (:q IS NULL
			       OR (:field = 'memberId' AND CAST(m.id AS CHAR) LIKE CONCAT('%', :q, '%'))
			       OR (:field = 'email' AND LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'nickname' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))))
			ORDER BY mtns.created_at DESC
			""",
			countQuery = """
			SELECT COUNT(*)
			FROM member_team_notification_subscription mtns
			JOIN member m ON m.id = mtns.member_id
			WHERE mtns.team_id = :teamId
			  AND (:q IS NULL
			       OR (:field = 'memberId' AND CAST(m.id AS CHAR) LIKE CONCAT('%', :q, '%'))
			       OR (:field = 'email' AND LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%')))
			       OR (:field = 'nickname' AND LOWER(CONCAT(m.name, '#', m.tag)) LIKE LOWER(CONCAT('%', :q, '%'))))
			""",
			nativeQuery = true)
	Page<TeamSubscriberView> findSubscribersByTeamId(@Param("teamId") Long teamId,
			@Param("field") String field, @Param("q") String q, Pageable pageable);

	interface TeamSubscriberView {
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
