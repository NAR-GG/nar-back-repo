package com.toy.nar.game.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "games", indexes = {
	@Index(name = "idx_game_date", columnList = "game_date"),
	@Index(name = "idx_game_league", columnList = "league_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"league", "bans"})
public class Game {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "game_id")
	private Long id;

	@Column(name = "game_origin_id", unique = true)
	private String gameOriginId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "league_id", nullable = false)
	private League league;

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<GameParticipant> participants = new HashSet<>();

	@Column(name = "game_date", nullable = false)
	private LocalDate gameDate;

	@Column(name = "game_number", nullable = false)
	private Integer gameNumber;

	@Column(name = "patch", nullable = false, length = 20)
	private String patch;

	@Column(name = "game_length_seconds", nullable = false)
	private Integer gameLengthSeconds;

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<Ban> bans = new HashSet<>();

	@Builder
	public Game(String gameOriginId, League league, LocalDate gameDate, Integer gameNumber, String patch, Integer gameLengthSeconds) {
		this.gameOriginId = gameOriginId;
		this.league = league;
		this.gameDate = gameDate;
		this.gameNumber = gameNumber;
		this.patch = patch;
		this.gameLengthSeconds = gameLengthSeconds;
	}
}