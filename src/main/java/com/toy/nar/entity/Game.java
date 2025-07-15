package com.toy.nar.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import java.util.List;

@Entity
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"league", "bans"})
public class Game {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "game_id")
	private Long id;

	@Column(name = "game_origin_id")
	private String gameOriginId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "league_id", nullable = false)
	private League league;

	@Column(name = "game_date", nullable = false)
	private LocalDate gameDate;

	@Column(name = "game_number", nullable = false)
	private Integer gameNumber;

	@Column(name = "patch", nullable = false, length = 20)
	private String patch;

	@Column(name = "game_length_seconds", nullable = false)
	private Integer gameLengthSeconds;

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Ban> bans = new ArrayList<>();

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