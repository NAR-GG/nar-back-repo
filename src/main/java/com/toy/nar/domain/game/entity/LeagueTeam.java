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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.toy.nar.domain.participant.entity.Team;

@Entity
@Table(name = "league_teams", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "league_id", "team_id" })
}, indexes = {
		@Index(name = "idx_league_teams_league", columnList = "league_id"),
		@Index(name = "idx_league_teams_team", columnList = "team_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = { "league", "team" })
@ToString(exclude = { "league", "team" })
public class LeagueTeam {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "league_team_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "league_id", nullable = false)
	private League league;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Builder
	public LeagueTeam(League league, Team team) {
		this.league = league;
		this.team = team;
	}
}
