package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberTeamEventPushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라이브 경기 팀 이벤트 FCM 푸시(#21) 멱등 리포지토리.
 * PlayerSoloRankPushDeliveryRepository 의 reserve / markSent / markFailed 패턴을 복제했다.
 * 멱등 키는 (member_id, match_id, set_number, event_type, event_order).
 */
public interface MemberTeamEventPushDeliveryRepository
		extends JpaRepository<MemberTeamEventPushDelivery, Long> {

	/*
	 * [중복 발송 버그 수정] 기존 reserve 는 INSERT ... ON DUPLICATE KEY UPDATE + IF 패턴으로
	 * "변경된 행 수 0 = 이미 처리됨"을 판정했다. 그러나 MySQL Connector/J 기본 설정
	 * (CLIENT_FOUND_ROWS)에서는 값이 바뀌지 않은 duplicate 도 0이 아닌 값을 반환해
	 * 이미 SENT 인 건이 매번 dedup 을 통과했다(세트 종료 알림 반복 발송의 원인).
	 * 아래처럼 INSERT IGNORE(신규)와 WHERE 조건 UPDATE(재예약)로 분리하면
	 * found-rows 모드에서도 반환값이 정확하다 — WHERE 가 대상 행을 필터하기 때문.
	 */

	/** 신규 예약. 이미 같은 키가 있으면 0. */
	@Modifying
	@Transactional
	@Query(value = """
			INSERT IGNORE INTO member_team_event_push_delivery (
				member_id, match_id, set_number, event_type, event_order,
				status, created_at, updated_at
			) VALUES (
				:memberId, :matchId, :setNumber, :eventType, :eventOrder,
				'PENDING', NOW(), NOW()
			)
			""", nativeQuery = true)
	int insertIfAbsent(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder);

	/** 재예약. 실패했거나 5분 넘게 PENDING 으로 방치된 건만 되살린다. SENT 는 절대 재예약되지 않는다. */
	@Modifying
	@Transactional
	@Query(value = """
			UPDATE member_team_event_push_delivery
			SET status = 'PENDING', error_message = NULL, updated_at = NOW()
			WHERE member_id = :memberId
			  AND match_id = :matchId
			  AND set_number = :setNumber
			  AND event_type = :eventType
			  AND event_order = :eventOrder
			  AND (status = 'FAILED'
					OR (status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)))
			""", nativeQuery = true)
	int reactivateStale(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder);

	/** 발송 예약. 신규이거나 재시도 대상일 때만 true. */
	default boolean reserve(Long memberId, String matchId, int setNumber, String eventType, long eventOrder) {
		if (insertIfAbsent(memberId, matchId, setNumber, eventType, eventOrder) > 0) {
			return true;
		}
		return reactivateStale(memberId, matchId, setNumber, eventType, eventOrder) > 0;
	}

	@Modifying
	@Transactional
	@Query("""
			UPDATE MemberTeamEventPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.SENT,
			    delivery.errorMessage = null,
			    delivery.sentAt = CURRENT_TIMESTAMP,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.matchId = :matchId
			  AND delivery.setNumber = :setNumber
			  AND delivery.eventType = :eventType
			  AND delivery.eventOrder = :eventOrder
			""")
	int markSent(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder);

	@Modifying
	@Transactional
	@Query("""
			UPDATE MemberTeamEventPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.FAILED,
			    delivery.errorMessage = :errorMessage,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.matchId = :matchId
			  AND delivery.setNumber = :setNumber
			  AND delivery.eventType = :eventType
			  AND delivery.eventOrder = :eventOrder
			""")
	int markFailed(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder,
			@Param("errorMessage") String errorMessage);
}
