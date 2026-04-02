package com.toy.nar.domain.participant.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.toy.nar.domain.game.entity.LeagueTeam;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString
public class Team {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "team_id")
	private Long id;

	@Column(name = "team_origin_id")
	private String teamOriginId;

	@Column(name = "team_name", nullable = false, unique = true, length = 100)
	private String name;

	@Column(name = "team_code", length = 10)
	private String code;

	@Column(name = "team_image_url")
	private String imageUrl;

	@OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<LeagueTeam> leagueTeams = new ArrayList<>();

	@Builder
	public Team(String name, String code, String imageUrl) {
		this.name = Objects.requireNonNull(name, "Team name must not be null");
		this.code = code;
		this.imageUrl = imageUrl;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void updateMetadata(String code, String imageUrl) {
		this.updateMetadata(this.name, code, imageUrl);
	}

	public void updateMetadata(String name, String code, String imageUrl) {
		this.name = Objects.requireNonNull(name, "Team name must not be null");
		this.code = code;
		this.imageUrl = imageUrl;
	}
}
