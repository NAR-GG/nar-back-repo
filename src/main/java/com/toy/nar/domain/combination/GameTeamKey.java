package com.toy.nar.domain.combination;

import java.util.Objects;

import lombok.Getter;

@Getter
public class GameTeamKey {
	private final Long gameId;
	private final String teamName;

	public GameTeamKey(Long gameId, String teamName) {
		this.gameId = Objects.requireNonNull(gameId);
		this.teamName = Objects.requireNonNull(teamName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		GameTeamKey that = (GameTeamKey) obj;
		return Objects.equals(gameId, that.gameId) &&
			Objects.equals(teamName, that.teamName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gameId, teamName);
	}

	@Override
	public String toString() {
		return String.format("GameTeamKey{gameId=%d, teamName='%s'}", gameId, teamName);
	}
}
