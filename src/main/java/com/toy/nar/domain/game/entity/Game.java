package com.toy.nar.domain.game.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.toy.nar.app.data.ingestion.GameProcessor;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;

@Entity
@Table(name = "games", indexes = {
		@Index(name = "idx_scheduled_time", columnList = "scheduled_game_start_time"),
		@Index(name = "idx_game_league", columnList = "league_id"),
		@Index(name = "idx_actual_game_start_time", columnList = "actual_game_start_time")
})
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = { "league", "bans" })
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
	@Builder.Default
	private List<GameParticipant> participants = new ArrayList<>();

	@OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@Builder.Default
	private Set<Ban> bans = new HashSet<>();

	@Column(name = "actual_game_start_time", nullable = false)
	private LocalDateTime actualGameStartTime;

	@Column(name = "scheduled_game_start_time")
	private LocalDateTime scheduledGameStartTime;

	@Column(name = "game_number", nullable = false)
	private Integer gameNumber;

	@Column(name = "patch", nullable = false, length = 20)
	private String patch;

	@Column(name = "game_length_seconds", nullable = false)
	private Integer gameLengthSeconds;

	@Column(name = "ckpm", nullable = false)
	private Double ckpm;

	public void addParticipant(GameParticipant participant) {
		participants.add(participant);
		participant.assignGame(this);
	}

	public static Game from(GameDataCsvDto dto, League league, LocalDateTime scheduledGameStartTime) {
		return Game.builder()
				.gameOriginId(dto.getGameid())
				.league(league)
				.actualGameStartTime(LocalDateTime.parse(dto.getDate(), GameProcessor.CSV_DATE_FORMATTER))
				.scheduledGameStartTime(scheduledGameStartTime)
				.gameNumber(dto.getGame())
				.patch(dto.getPatch())
				.gameLengthSeconds(dto.getGamelength())
				.ckpm(dto.getCkpm())
				.build();
	}
}