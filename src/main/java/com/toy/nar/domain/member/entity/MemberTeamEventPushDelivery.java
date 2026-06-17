package com.toy.nar.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 라이브 경기 팀 이벤트 FCM 푸시(#21) 멱등 처리 테이블.
 * 유니크 키 (member_id, match_id, set_number, event_type, event_order) 로 한 회원당 이벤트 1회 발송을 보장한다.
 * 키가 팀이 아니라 match_id 라서, 한 회원이 한 경기의 양 팀을 모두 구독해도 이벤트당 1번만 발송된다(두 번째 팀 reserve()=0).
 * PlayerSoloRankPushDelivery 패턴을 그대로 복제했다.
 */
@Entity
@Table(name = "member_team_event_push_delivery", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_member_team_event_push_delivery",
				columnNames = { "member_id", "match_id", "set_number", "event_type", "event_order" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTeamEventPushDelivery {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "match_id", nullable = false, length = 64)
	private String matchId;

	@Column(name = "set_number", nullable = false)
	private int setNumber;

	@Column(name = "event_type", nullable = false, length = 20)
	private String eventType;

	@Column(name = "event_order", nullable = false)
	private long eventOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PushDeliveryStatus status;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
