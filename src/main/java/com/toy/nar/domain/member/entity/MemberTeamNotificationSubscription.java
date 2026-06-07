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

	@Column(name = "live_event_enabled", nullable = false)
	private boolean liveEventEnabled;

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
