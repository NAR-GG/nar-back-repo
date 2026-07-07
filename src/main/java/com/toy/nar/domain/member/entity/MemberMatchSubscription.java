package com.toy.nar.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 특정 경기 예약 알림 구독. 팀 구독과 별개로, 유저가 개별 경기를 구독하면
 * 해당 경기의 세트 시작/종료/라이브 이벤트를 받는다(토글 없이 3종 전부).
 * match_id 는 league_match.id (외부 경기 식별자, VARCHAR).
 */
@Entity
@Table(name = "member_match_subscription", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_member_match_subscription",
				columnNames = { "member_id", "match_id" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberMatchSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "match_id", nullable = false, length = 50)
	private String matchId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public MemberMatchSubscription(Member member, String matchId) {
		this.member = Objects.requireNonNull(member);
		this.matchId = Objects.requireNonNull(matchId);
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
