package com.toy.nar.domain.member.entity;

import com.toy.nar.domain.participant.entity.Player;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_solo_rank_push_delivery", uniqueConstraints = {
		@UniqueConstraint(
				name = "uk_player_solo_rank_push_delivery",
				columnNames = {"member_id", "player_id", "game_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSoloRankPushDelivery {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@Column(name = "game_id", nullable = false, length = 64)
	private String gameId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PushDeliveryStatus status;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
