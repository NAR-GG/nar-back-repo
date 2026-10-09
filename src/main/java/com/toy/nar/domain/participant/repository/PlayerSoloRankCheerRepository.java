package com.toy.nar.domain.participant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 솔랭 응원 누적. 엔티티 없이 SQL 로 직접 다룬다 — 쓰기는 upsert 한 줄이고 읽기는 합산뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class PlayerSoloRankCheerRepository {

	/** 한 판의 식별자. */
	public record GameKey(Long playerId, String gameId) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	/** 회원의 (선수, 판) 행에 횟수를 더한다. 없으면 만든다. */
	public void add(Long playerId, String gameId, Long memberId, int count) {
		jdbc.update("INSERT INTO player_solo_rank_cheer (player_id, game_id, member_id, cheer_count)"
						+ " VALUES (:playerId, :gameId, :memberId, :count)"
						+ " ON DUPLICATE KEY UPDATE cheer_count = cheer_count + VALUES(cheer_count),"
						+ " updated_at = CURRENT_TIMESTAMP(3)",
				new MapSqlParameterSource()
						.addValue("playerId", playerId)
						.addValue("gameId", gameId)
						.addValue("memberId", memberId)
						.addValue("count", count));
	}

	/** 판별 전체 응원 합계. 응원이 없는 판은 키가 없다. */
	public Map<GameKey, Long> totals(Collection<Long> playerIds, Collection<String> gameIds) {
		if (playerIds.isEmpty() || gameIds.isEmpty()) {
			return Map.of();
		}
		Map<GameKey, Long> result = new HashMap<>();
		jdbc.query("SELECT player_id, game_id, SUM(cheer_count) AS total FROM player_solo_rank_cheer"
						+ " WHERE player_id IN (:playerIds) AND game_id IN (:gameIds)"
						+ " GROUP BY player_id, game_id",
				new MapSqlParameterSource().addValue("playerIds", playerIds).addValue("gameIds", gameIds),
				rs -> {
					result.put(new GameKey(rs.getLong("player_id"), rs.getString("game_id")), rs.getLong("total"));
				});
		return result;
	}

	/** 판별 내 응원 횟수. */
	public Map<GameKey, Long> mine(Long memberId, Collection<Long> playerIds, Collection<String> gameIds) {
		if (playerIds.isEmpty() || gameIds.isEmpty()) {
			return Map.of();
		}
		Map<GameKey, Long> result = new HashMap<>();
		jdbc.query("SELECT player_id, game_id, cheer_count FROM player_solo_rank_cheer"
						+ " WHERE member_id = :memberId AND player_id IN (:playerIds) AND game_id IN (:gameIds)",
				new MapSqlParameterSource()
						.addValue("memberId", memberId)
						.addValue("playerIds", playerIds)
						.addValue("gameIds", gameIds),
				rs -> {
					result.put(new GameKey(rs.getLong("player_id"), rs.getString("game_id")),
							rs.getLong("cheer_count"));
				});
		return result;
	}
}
