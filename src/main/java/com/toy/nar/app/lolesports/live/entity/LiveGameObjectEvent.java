package com.toy.nar.app.lolesports.live.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_game_object_event", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_game_object_event", columnNames = {
				"game_id", "team_side", "event_type", "event_order" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveGameObjectEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false, length = 64)
	private String gameId;

	@Column(name = "match_id", length = 64)
	private String matchId;

	@Column(name = "league_name", length = 20)
	private String leagueName;

	@Column(name = "team_side", nullable = false, length = 8)
	private String teamSide;

	@Column(name = "event_type", nullable = false, length = 20)
	private String eventType;

	@Column(name = "event_sub_type", length = 50)
	private String eventSubType;

	@Column(name = "event_order", nullable = false)
	private Integer eventOrder;

	@Column(name = "value_after", nullable = false)
	private Integer valueAfter;

	@Column(name = "source_frame_timestamp_utc", nullable = false)
	private LocalDateTime sourceFrameTimestampUtc;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public LiveGameObjectEvent(
			String gameId,
			String matchId,
			String leagueName,
			String teamSide,
			String eventType,
			String eventSubType,
			Integer eventOrder,
			Integer valueAfter,
			LocalDateTime sourceFrameTimestampUtc) {
		this.gameId = gameId;
		this.matchId = matchId;
		this.leagueName = leagueName;
		this.teamSide = teamSide;
		this.eventType = eventType;
		this.eventSubType = eventSubType;
		this.eventOrder = eventOrder;
		this.valueAfter = valueAfter;
		this.sourceFrameTimestampUtc = sourceFrameTimestampUtc;
	}
}
