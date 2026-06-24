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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 마이구독 알림 피드 1건. 푸시 발송 성공 시점에 기록된다.
 * 조회 전용에 가깝고, 읽음 처리({@link #markRead()})만 상태를 바꾼다.
 */
@Entity
@Table(name = "member_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private MemberNotificationType type;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Column(name = "body", length = 500)
	private String body;

	/** 딥링크·참조 식별자(playerId/matchId/gameId/setNumber 등). 푸시 data 를 그대로 보존한다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "data")
	private Map<String, String> data;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public MemberNotification(
			Member member,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data) {
		this.member = member;
		this.type = type;
		this.title = title;
		this.body = body;
		this.data = data;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isRead() {
		return readAt != null;
	}

	public void markRead() {
		if (readAt == null) {
			this.readAt = LocalDateTime.now();
		}
	}
}
