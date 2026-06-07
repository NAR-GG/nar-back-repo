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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "member_device", uniqueConstraints = {
		@UniqueConstraint(name = "uk_member_device_fcm_token", columnNames = "fcm_token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDevice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "fcm_token", nullable = false, length = 512)
	private String fcmToken;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, length = 20)
	private MobileDevicePlatform platform;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "last_registered_at", nullable = false)
	private LocalDateTime lastRegisteredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public MemberDevice(
			Member member,
			String fcmToken,
			MobileDevicePlatform platform) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.fcmToken = Objects.requireNonNull(fcmToken, "fcmToken must not be null");
		this.platform = Objects.requireNonNull(platform, "platform must not be null");
		this.active = true;
		this.lastRegisteredAt = LocalDateTime.now();
	}

	public void register(Member member, MobileDevicePlatform platform) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.platform = Objects.requireNonNull(platform, "platform must not be null");
		this.active = true;
		this.lastRegisteredAt = LocalDateTime.now();
	}

	public void deactivate() {
		this.active = false;
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (lastRegisteredAt == null) {
			lastRegisteredAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
