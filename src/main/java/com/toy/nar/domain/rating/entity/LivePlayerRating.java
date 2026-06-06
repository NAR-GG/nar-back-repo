package com.toy.nar.domain.rating.entity;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Player;
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
@Table(name = "live_player_rating", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_live_player_rating_member_target",
				columnNames = { "live_game_id", "live_participant_id", "member_id" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LivePlayerRating {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "live_game_id", nullable = false, length = 64)
	private String liveGameId;

	@Column(name = "live_participant_id", nullable = false)
	private Integer liveParticipantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "internal_player_id")
	private Player player;

	@Column(name = "team_side", length = 8)
	private String teamSide;

	@Column(name = "role", length = 20)
	private String role;

	@Column(name = "player_name", nullable = false, length = 100)
	private String playerName;

	@Column(name = "esports_player_id", length = 64)
	private String esportsPlayerId;

	@Column(name = "champion_name", length = 50)
	private String championName;

	@Column(nullable = false)
	private Integer rating;

	@Column(length = 150)
	private String comment;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public LivePlayerRating(
			String liveGameId,
			Integer liveParticipantId,
			Member member,
			Player player,
			String teamSide,
			String role,
			String playerName,
			String esportsPlayerId,
			String championName,
			Integer rating,
			String comment) {
		this.liveGameId = Objects.requireNonNull(liveGameId);
		this.liveParticipantId = Objects.requireNonNull(liveParticipantId);
		this.member = Objects.requireNonNull(member);
		this.player = player;
		this.teamSide = teamSide;
		this.role = role;
		this.playerName = Objects.requireNonNull(playerName);
		this.esportsPlayerId = esportsPlayerId;
		this.championName = championName;
		update(rating, comment);
	}

	public void update(Integer rating, String comment) {
		if (rating == null || rating < 1 || rating > 5) {
			throw new IllegalArgumentException("별점은 1점 이상 5점 이하여야 합니다.");
		}
		if (comment != null && comment.length() > 150) {
			throw new IllegalArgumentException("한줄평은 150자 이하여야 합니다.");
		}
		this.rating = rating;
		this.comment = comment == null || comment.isBlank() ? null : comment.trim();
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
