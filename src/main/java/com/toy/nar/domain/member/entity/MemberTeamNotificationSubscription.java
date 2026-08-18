package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "member_team_notification_subscription", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_member_team_notification_subscription",
				columnNames = { "member_id", "team_id" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTeamNotificationSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(name = "set_start_enabled", nullable = false)
	private boolean setStartEnabled;

	@Column(name = "set_end_enabled", nullable = false)
	private boolean setEndEnabled;

	/** 라이브 이벤트 마스터 스위치. 아래 종류별 토글과 AND 로 걸린다. */
	@Column(name = "live_event_enabled", nullable = false)
	private boolean liveEventEnabled;

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

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public MemberTeamNotificationSubscription(Member member, Team team) {
		this.member = Objects.requireNonNull(member);
		this.team = Objects.requireNonNull(team);
		this.setStartEnabled = true;
		this.setEndEnabled = true;
		this.liveEventEnabled = false;
	}

	public void update(boolean setStartEnabled, boolean setEndEnabled, boolean liveEventEnabled) {
		this.setStartEnabled = setStartEnabled;
		this.setEndEnabled = setEndEnabled;
		this.liveEventEnabled = liveEventEnabled;
	}

	/**
	 * 라이브 이벤트 종류별 토글을 갱신한다. null 은 건드리지 않는다 — 구버전 앱이 이 필드를
	 * 안 보내는데 false 로 덮으면 받던 알림이 조용히 끊긴다.
	 */
	public void updateLiveEventTypes(
			Boolean killEnabled, Boolean baronEnabled, Boolean dragonEnabled,
			Boolean towerEnabled, Boolean inhibitorEnabled) {
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
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
