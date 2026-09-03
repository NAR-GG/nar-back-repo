package com.toy.nar.app.lolesports.live.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "live_game_minute_participant_snapshot", uniqueConstraints = {
		@UniqueConstraint(name = "uk_live_minute_participant", columnNames = { "snapshot_id", "participant_id" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveGameMinuteParticipantSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "snapshot_id", nullable = false)
	private LiveGameMinuteSnapshot snapshot;

	@Column(name = "participant_id", nullable = false)
	private Integer participantId;

	@Column(name = "team_side", length = 8)
	private String teamSide;

	@Column(name = "role", length = 20)
	private String role;

	@Column(name = "player_name", length = 100)
	private String playerName;

	@Column(name = "esports_player_id", length = 64)
	private String esportsPlayerId;

	@Column(name = "champion_name", length = 50)
	private String championName;

	@Column(name = "level")
	private Integer level;

	@Column(name = "kills")
	private Integer kills;

	@Column(name = "deaths")
	private Integer deaths;

	@Column(name = "assists")
	private Integer assists;

	@Column(name = "total_gold_earned")
	private Integer totalGoldEarned;

	@Column(name = "creep_score")
	private Integer creepScore;

	@Column(name = "kill_participation")
	private Double killParticipation;

	@Column(name = "champion_damage_share")
	private Double championDamageShare;

	/** 설치한 와드 수(일반+제어 합산, 누적). 피드 wardsPlaced. V87 이전 행은 null. */
	@Column(name = "wards_placed")
	private Integer wardsPlaced;

	/** 부순 상대 와드 수(누적). 피드 wardsDestroyed. */
	@Column(name = "wards_destroyed")
	private Integer wardsDestroyed;

	@Column(name = "item_ids_json", columnDefinition = "TEXT")
	private String itemIdsJson;

	@Column(name = "perks_json", columnDefinition = "TEXT")
	private String perksJson;

	public LiveGameMinuteParticipantSnapshot(
			LiveGameMinuteSnapshot snapshot,
			Integer participantId,
			String teamSide,
			String role,
			String playerName,
			String esportsPlayerId,
			String championName,
			Integer level,
			Integer kills,
			Integer deaths,
			Integer assists,
			Integer totalGoldEarned,
			Integer creepScore,
			Double killParticipation,
			Double championDamageShare,
			String itemIdsJson,
			String perksJson,
			Integer wardsPlaced,
			Integer wardsDestroyed) {
		this.snapshot = snapshot;
		this.participantId = participantId;
		this.teamSide = teamSide;
		this.role = role;
		this.playerName = playerName;
		this.esportsPlayerId = esportsPlayerId;
		this.championName = championName;
		this.level = level;
		this.kills = kills;
		this.deaths = deaths;
		this.assists = assists;
		this.totalGoldEarned = totalGoldEarned;
		this.creepScore = creepScore;
		this.killParticipation = killParticipation;
		this.championDamageShare = championDamageShare;
		this.itemIdsJson = itemIdsJson;
		this.perksJson = perksJson;
		this.wardsPlaced = wardsPlaced;
		this.wardsDestroyed = wardsDestroyed;
	}
}

