package com.toy.nar.domain.participant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@ToString
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

	@Builder
	public Player(String name, String imageUrl) {
		this.name = Objects.requireNonNull(name, "Player name must not be null");
		this.imageUrl = imageUrl;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
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
