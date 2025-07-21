package com.toy.nar.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.Objects;

import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;

@Entity
@Table(name = "game_participants", indexes = {
	@Index(name = "idx_game_champion", columnList = "game_id, champion_id"),
	@Index(name = "idx_gameparticipant_player", columnList = "player_id"),
	@Index(name = "idx_gameparticipant_team", columnList = "team_id")
})@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"game", "player", "team", "champion"}) // 연관 관계 필드는 toString에서 제외
public class GameParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "participant_game_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(name = "side", nullable = false, length = 10) // Blue 또는 Red
	private String side;

	@Column(name = "position", nullable = false, length = 20) // top, jungle, mid, bot, sup
	private String position;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "champion_id", nullable = false) // champions 테이블의 FK
	private Champion champion;

	@Column(name = "is_win", nullable = false) // CSV 'result' (1/0)을 Boolean으로 변환
	private Boolean isWin;

	public void assignGame(Game game) {
		this.game = game;
	}

	@Builder
	public GameParticipant(Game game, Player player, Team team, String side, String position, Champion champion, Boolean isWin) {
		this.game = Objects.requireNonNull(game, "Game must not be null");
		this.player = Objects.requireNonNull(player, "Player must not be null");
		this.team = Objects.requireNonNull(team, "Team must not be null");
		this.side = Objects.requireNonNull(side, "Side must not be null");
		this.position = Objects.requireNonNull(position, "Position must not be null");
		this.champion = Objects.requireNonNull(champion);
		this.isWin = Objects.requireNonNull(isWin, "Is win must not be null");
	}
}