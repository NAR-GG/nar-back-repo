package com.toy.nar.domain.participant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

@Entity
@Table(name = "players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = "currentTeam")
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "player_id")
	private Long id;

	@Column(name = "player_origin_id")
	private String playerOriginId;

	@Column(name = "player_name", nullable = false, unique = true, length = 100)
	private String name;

	@Column(name = "image_url")
	private String imageUrl;

	@Column(name = "real_name", length = 100)
	private String realName;

	@Column(name = "birth_date", length = 10)
	private String birthDate;

	@Column(name = "age")
	private Integer age;

	@Column(name = "role", length = 20)
	private String role;

	@Column(name = "game_accounts", columnDefinition = "JSON")
	private String gameAccounts;

	// 백오피스에서 수동 관리하는 현재 소속팀. 경기 기록(GameParticipant)과 무관 — sync가 건드리지 않는다.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_team_id")
	private Team currentTeam;

	// true면 자동 동기화가 imageUrl을 덮어쓰지 못한다(setImageUrl no-op).
	@Column(name = "image_locked", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
	private boolean imageLocked;

	@Builder
	public Player(String name, String imageUrl) {
		this.name = Objects.requireNonNull(name, "Player name must not be null");
		this.imageUrl = imageUrl;
	}

	public void setName(String name) {
		this.name = name;
	}

	// 자동 동기화 경로. 수동 잠금 상태면 무시 — 모든 sync 호출부(PlayerService, PlayerImageMigrationService)가 이 한 곳으로 보호된다.
	public void setImageUrl(String imageUrl) {
		if (imageLocked) {
			return;
		}
		this.imageUrl = imageUrl;
	}

	// 백오피스 수동 수정: 이미지 교체 + sync 잠금.
	public void overrideImage(String imageUrl) {
		this.imageUrl = imageUrl;
		this.imageLocked = true;
	}

	public void unlockImage() {
		this.imageLocked = false;
	}

	public void changeCurrentTeam(Team team) {
		this.currentTeam = team;
	}

	public void updateProfile(String realName, String birthDate, Integer age,
			String role, String gameAccounts) {
		this.realName = realName;
		this.birthDate = birthDate;
		this.age = age;
		this.role = role;
		this.gameAccounts = gameAccounts;
	}
}
