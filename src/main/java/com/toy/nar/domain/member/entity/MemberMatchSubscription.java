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
 * 해당 경기의 세트 시작/종료/라이브 이벤트를 받는다. 팀 구독과 동일하게
 * 종류별 토글(set_start/set_end/live_event)을 가진다.
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

	@Column(name = "set_start_enabled", nullable = false)
	private boolean setStartEnabled = true;

	@Column(name = "set_end_enabled", nullable = false)
	private boolean setEndEnabled = true;

	/** 라이브 이벤트 마스터 스위치. 아래 종류별 토글과 AND 로 걸린다. */
	@Column(name = "live_event_enabled", nullable = false)
	private boolean liveEventEnabled = true;

	@Column(name = "kill_enabled", nullable = false)
	private boolean killEnabled = true;

	@Column(name = "baron_enabled", nullable = false)
	private boolean baronEnabled = true;

	@Column(name = "dragon_enabled", nullable = false)
	private boolean dragonEnabled = true;

	@Column(name = "tower_enabled", nullable = false)
	private boolean towerEnabled = true;

	@Column(name = "inhibitor_enabled", nullable = false)
	private boolean inhibitorEnabled = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public MemberMatchSubscription(
			Member member, String matchId,
			boolean setStartEnabled, boolean setEndEnabled, boolean liveEventEnabled) {
		this.member = Objects.requireNonNull(member);
		this.matchId = Objects.requireNonNull(matchId);
		this.setStartEnabled = setStartEnabled;
		this.setEndEnabled = setEndEnabled;
		this.liveEventEnabled = liveEventEnabled;
	}

	/**
	 * 알림 토글을 갱신한다. null 인 값은 건드리지 않는다 — 구버전 앱이 모르는 필드를
	 * 안 보내면 그 토글이 꺼진 것으로 오해하면 안 된다.
	 */
	public void updateToggles(
			Boolean setStartEnabled, Boolean setEndEnabled, Boolean liveEventEnabled,
			Boolean killEnabled, Boolean baronEnabled, Boolean dragonEnabled,
			Boolean towerEnabled, Boolean inhibitorEnabled) {
		if (setStartEnabled != null) {
			this.setStartEnabled = setStartEnabled;
		}
		if (setEndEnabled != null) {
			this.setEndEnabled = setEndEnabled;
		}
		if (liveEventEnabled != null) {
			this.liveEventEnabled = liveEventEnabled;
		}
		if (killEnabled != null) {
			this.killEnabled = killEnabled;
		}
		if (baronEnabled != null) {
			this.baronEnabled = baronEnabled;
		}
		if (dragonEnabled != null) {
			this.dragonEnabled = dragonEnabled;
		}
		if (towerEnabled != null) {
			this.towerEnabled = towerEnabled;
		}
		if (inhibitorEnabled != null) {
			this.inhibitorEnabled = inhibitorEnabled;
		}
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
