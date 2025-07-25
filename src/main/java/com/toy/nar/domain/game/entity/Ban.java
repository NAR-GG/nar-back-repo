package com.toy.nar.domain.game.entity;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Objects;

import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Team;

@Entity
@Table(name = "bans", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"game_id", "banned_champion_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"game", "bannedChampion"})
@ToString(exclude = {"game", "team", "bannedChampion"}) // 연관 관계 필드는 toString에서 제외
public class Ban {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ban_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "banned_champion_id", nullable = false)
	private Champion bannedChampion;

	@Builder
	public Ban(Game game, Team team, Champion bannedChampion) {
		this.game = Objects.requireNonNull(game, "Game must not be null");
		this.team = Objects.requireNonNull(team, "Team must not be null");
		this.bannedChampion = Objects.requireNonNull(bannedChampion);
	}
}