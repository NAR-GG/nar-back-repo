package com.toy.nar.app.lolesports.live.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "live_game_minute_snapshot", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_game_minute_bucket", columnNames = { "game_id", "minute_bucket_utc" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveGameMinuteSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false, length = 64)
	private String gameId;

	@Column(name = "match_id", length = 64)
	private String matchId;

	@Column(name = "league_name", length = 20)
	private String leagueName;

	@Column(name = "blue_team_name", length = 120)
	private String blueTeamName;

	@Column(name = "red_team_name", length = 120)
	private String redTeamName;

	@Column(name = "minute_bucket_utc", nullable = false)
	private LocalDateTime minuteBucketUtc;

	@Column(name = "frame_timestamp_utc", nullable = false)
	private LocalDateTime frameTimestampUtc;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public LiveGameMinuteSnapshot(String gameId, LocalDateTime minuteBucketUtc) {
		this.gameId = Objects.requireNonNull(gameId);
		this.minuteBucketUtc = Objects.requireNonNull(minuteBucketUtc);
	}

	public void updateSnapshot(
			String matchId,
			String leagueName,
			String blueTeamName,
			String redTeamName,
			LocalDateTime frameTimestampUtc) {
		this.matchId = matchId;
		this.leagueName = leagueName;
		this.blueTeamName = blueTeamName;
		this.redTeamName = redTeamName;
		this.frameTimestampUtc = Objects.requireNonNull(frameTimestampUtc);
	}

	@PrePersist
	public void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	public void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}

