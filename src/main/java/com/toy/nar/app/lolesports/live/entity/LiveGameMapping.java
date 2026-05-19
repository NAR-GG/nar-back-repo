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
@Table(name = "live_game_mapping", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_game_mapping_live_game", columnNames = "live_game_id") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveGameMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "live_game_id", nullable = false, length = 64)
	private String liveGameId;

	@Column(name = "live_match_id", length = 64)
	private String liveMatchId;

	@Column(name = "live_league_name", length = 20)
	private String liveLeagueName;

	@Column(name = "live_blue_team_name", length = 120)
	private String liveBlueTeamName;

	@Column(name = "live_red_team_name", length = 120)
	private String liveRedTeamName;

	@Column(name = "first_minute_bucket_utc")
	private LocalDateTime firstMinuteBucketUtc;

	@Column(name = "last_frame_timestamp_utc")
	private LocalDateTime lastFrameTimestampUtc;

	@Column(name = "internal_game_id")
	private Long internalGameId;

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

	public LiveGameMapping(String liveGameId) {
		this.liveGameId = Objects.requireNonNull(liveGameId);
		this.status = MappingStatus.PENDING;
	}

	public void updateLiveContext(
			String liveMatchId,
			String liveLeagueName,
			String liveBlueTeamName,
			String liveRedTeamName,
			LocalDateTime firstMinuteBucketUtc,
			LocalDateTime lastFrameTimestampUtc) {
		this.liveMatchId = liveMatchId;
		this.liveLeagueName = liveLeagueName;
		this.liveBlueTeamName = liveBlueTeamName;
		this.liveRedTeamName = liveRedTeamName;
		this.firstMinuteBucketUtc = firstMinuteBucketUtc;
		this.lastFrameTimestampUtc = lastFrameTimestampUtc;
	}

	public void markMapped(Long internalGameId, double confidence, String mappingMethod, String reason) {
		this.internalGameId = internalGameId;
		this.status = MappingStatus.MAPPED;
		this.confidence = confidence;
		this.mappingMethod = mappingMethod;
		this.reason = reason;
	}

	public void markPending(String reason) {
		this.status = MappingStatus.PENDING;
		this.confidence = null;
		this.mappingMethod = null;
		this.reason = reason;
		this.internalGameId = null;
	}

	public void markAmbiguous(String reason) {
		this.status = MappingStatus.AMBIGUOUS;
		this.confidence = null;
		this.mappingMethod = null;
		this.reason = reason;
		this.internalGameId = null;
	}
}
