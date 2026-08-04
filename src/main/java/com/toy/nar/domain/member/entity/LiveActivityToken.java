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
 * iOS Live Activity 카드 하나에 붙는 ActivityKit 푸시 토큰.
 *
 * <p>{@link MemberDevice} 의 FCM 토큰과 혼동하면 안 된다. 이 토큰은 APNs 가 발급하고
 * 액티비티 단위라, 같은 기기라도 경기마다 다른 값을 갖고 카드가 끝나면 죽는다.
 * FCM 으로는 이 토큰에 보낼 수 없어 APNs 직결 경로가 따로 있다.</p>
 */
@Entity
@Table(name = "live_activity_token", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_activity_token_push_token", columnNames = "push_token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveActivityToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "match_id", nullable = false, length = 64)
	private String matchId;

	@Column(name = "push_token", nullable = false, length = 512)
	private String pushToken;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public LiveActivityToken(Member member, String matchId, String pushToken) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.matchId = Objects.requireNonNull(matchId, "matchId must not be null");
		this.pushToken = Objects.requireNonNull(pushToken, "pushToken must not be null");
		this.active = true;
	}

	/**
	 * 같은 토큰이 다시 올라온 경우. 앱이 액티비티를 다시 띄우면 매치가 바뀔 수 있어 함께 갱신한다.
	 */
	public void reactivate(Member member, String matchId) {
		this.member = Objects.requireNonNull(member, "member must not be null");
		this.matchId = Objects.requireNonNull(matchId, "matchId must not be null");
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
