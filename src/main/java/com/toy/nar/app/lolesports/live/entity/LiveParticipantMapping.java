package com.toy.nar.app.lolesports.live.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "live_participant_mapping", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_participant_mapping", columnNames = { "live_game_id", "live_participant_id" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveParticipantMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "live_game_id", nullable = false, length = 64)
	private String liveGameId;

	@Column(name = "live_participant_id", nullable = false)
	private Integer liveParticipantId;

	@Column(name = "live_team_side", length = 8)
	private String liveTeamSide;

	@Column(name = "live_role", length = 20)
	private String liveRole;

	@Column(name = "live_player_name", length = 100)
	private String livePlayerName;

	@Column(name = "live_esports_player_id", length = 64)
	private String liveEsportsPlayerId;

	@Column(name = "live_champion_name", length = 50)
	private String liveChampionName;

	@Column(name = "internal_game_participant_id")
	private Long internalGameParticipantId;

	@Column(name = "internal_game_id")
	private Long internalGameId;

	@Column(name = "internal_player_id")
	private Long internalPlayerId;

	@Column(name = "internal_team_id")
	private Long internalTeamId;

	@Column(name = "internal_champion_id")
	private Long internalChampionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private MappingStatus status = MappingStatus.PENDING;

	@Column(name = "confidence")
	private Double confidence;

	@Column(name = "mapping_method", length = 64)
	private String mappingMethod;

	@Column(name = "reason", length = 500)
	private String reason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public LiveParticipantMapping(String liveGameId, Integer liveParticipantId) {
		this.liveGameId = Objects.requireNonNull(liveGameId);
		this.liveParticipantId = Objects.requireNonNull(liveParticipantId);
		this.status = MappingStatus.PENDING;
	}

	public void updateLiveContext(
			String liveTeamSide,
			String liveRole,
			String livePlayerName,
			String liveEsportsPlayerId,
			String liveChampionName) {
		this.liveTeamSide = liveTeamSide;
		this.liveRole = liveRole;
		this.livePlayerName = livePlayerName;
		this.liveEsportsPlayerId = liveEsportsPlayerId;
		this.liveChampionName = liveChampionName;
	}

	public void markMapped(
			Long internalGameParticipantId,
			Long internalGameId,
			Long internalPlayerId,
			Long internalTeamId,
			Long internalChampionId,
			double confidence,
			String mappingMethod,
			String reason) {
		this.internalGameParticipantId = internalGameParticipantId;
		this.internalGameId = internalGameId;
		this.internalPlayerId = internalPlayerId;
		this.internalTeamId = internalTeamId;
		this.internalChampionId = internalChampionId;
		this.status = MappingStatus.MAPPED;
		this.confidence = confidence;
		this.mappingMethod = mappingMethod;
		this.reason = reason;
	}

	public void markPending(String reason) {
		this.internalGameParticipantId = null;
		this.internalGameId = null;
		this.internalPlayerId = null;
		this.internalTeamId = null;
		this.internalChampionId = null;
		this.status = MappingStatus.PENDING;
		this.confidence = null;
		this.mappingMethod = null;
		this.reason = reason;
	}

	public void markAmbiguous(String reason) {
		this.internalGameParticipantId = null;
		this.internalGameId = null;
		this.internalPlayerId = null;
		this.internalTeamId = null;
		this.internalChampionId = null;
		this.status = MappingStatus.AMBIGUOUS;
		this.confidence = null;
		this.mappingMethod = null;
		this.reason = reason;
	}
}
