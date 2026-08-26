package com.toy.nar.domain.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 신고. target 은 다형 참조(POST/COMMENT/IMAGE)라 FK 가 없다 —
 * 실존·VISIBLE 검증은 서비스가 저장 전에 한다.
 * uk (target_type, target_id, reporter_id) 가 같은 대상 중복 신고를 막는다.
 */
@Entity
@Table(name = "community_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityReport {

	public enum TargetType {
		POST, COMMENT, IMAGE
	}

	public enum Reason {
		ABUSE, OBSCENE, AD, FRAUD, SPAM, ETC
	}

	public enum Status {
		PENDING, ACCEPTED, REJECTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private TargetType targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Reason reason;

	/** ETC 일 때 필수. */
	@Column(length = 200)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.PENDING;

	@Column(name = "handled_by")
	private Long handledBy;

	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	public CommunityReport(TargetType targetType, Long targetId, Long reporterId, Reason reason, String detail) {
		this.targetType = targetType;
		this.targetId = targetId;
		this.reporterId = reporterId;
		this.reason = reason;
		this.detail = detail;
		this.createdAt = LocalDateTime.now();
	}
}
