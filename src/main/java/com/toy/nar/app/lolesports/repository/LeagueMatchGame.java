package com.toy.nar.app.lolesports.repository;

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
import jakarta.persistence.Index;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "league_match_game", uniqueConstraints = {
		@UniqueConstraint(name = "uk_league_match_game_game_id", columnNames = "game_id"),
		@UniqueConstraint(name = "uk_league_match_game_match_game", columnNames = { "match_id", "game_id" }) }, indexes = {
				@Index(name = "idx_league_match_game_match_id", columnList = "match_id"),
				@Index(name = "idx_league_match_game_match_order", columnList = "match_id, game_order") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueMatchGame {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "match_id", nullable = false)
	private LeagueMatch leagueMatch;

	@Column(name = "game_id", nullable = false, length = 64)
	private String gameId;

	@Column(name = "game_order")
	private Integer gameOrder;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public LeagueMatchGame(LeagueMatch leagueMatch, String gameId, Integer gameOrder) {
		this.leagueMatch = Objects.requireNonNull(leagueMatch);
		this.gameId = Objects.requireNonNull(gameId);
		this.gameOrder = gameOrder;
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
