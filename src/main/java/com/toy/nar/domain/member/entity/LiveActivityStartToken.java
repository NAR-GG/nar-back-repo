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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * iOS push-to-start 토큰 (iOS 17.2+).
 *
 * <p>{@link LiveActivityToken} 과 헷갈리면 안 된다. 그쪽은 이미 떠 있는 카드 하나를 가리키는
 * 액티비티 단위 토큰이고, 이쪽은 앱 단위라 카드가 없어도 존재한다. 이 토큰으로만 서버가
 * 카드를 새로 만들 수 있다.</p>
 */
@Entity
@Table(name = "live_activity_start_token", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_activity_start_token_push_token", columnNames = "push_token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveActivityStartToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "push_token", nullable = false, length = 512)
	private String pushToken;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public LiveActivityStartToken(Member member, String pushToken) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.pushToken = Objects.requireNonNull(pushToken, "pushToken must not be null");
		this.active = true;
	}

	/** 앱이 같은 토큰을 다시 올린 경우(재설치·기기 이전 등)에 소유자를 갱신하고 되살린다. */
	public void reactivate(Member member) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.active = true;
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
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
