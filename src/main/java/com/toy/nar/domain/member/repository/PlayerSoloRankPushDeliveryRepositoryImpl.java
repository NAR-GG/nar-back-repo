package com.toy.nar.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PlayerSoloRankPushDeliveryRepositoryImpl
		implements PlayerSoloRankPushDeliveryRepositoryCustom {

	/** IN 절·VALUES 한 번에 넣을 최대 개수. SQL 이 과하게 길어지지 않게 나눈다. */
	private static final int CHUNK_SIZE = 500;

	/** PENDING 방치 재예약 기준(분). 솔랭은 5분이다 — 팀 이벤트(1분)와 값이 다르니 맞추지 말 것. */
	private static final String STALE_PENDING_MINUTES = "5";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public List<Long> reserveAll(Collection<Long> memberIds, Long playerId, String gameId) {
		List<Long> targets = new ArrayList<>();
		for (List<Long> chunk : chunk(memberIds)) {
			targets.addAll(reserveChunk(chunk, playerId, gameId));
		}
		return targets;
	}

	private List<Long> reserveChunk(List<Long> memberIds, Long playerId, String gameId) {
		// 1) 기존 행을 한 번에 읽어 신규/재예약/제외를 자바에서 가른다.
		//    단건 reserve 의 "INSERT IGNORE 실패 → 조건부 UPDATE" 판정을 그대로 재현한다.
		List<Object[]> existing = jdbcTemplate.query(
				"SELECT member_id, status, updated_at < DATE_SUB(NOW(), INTERVAL "
						+ STALE_PENDING_MINUTES + " MINUTE) AS stale"
						+ " FROM player_solo_rank_push_delivery"
						+ " WHERE player_id = ? AND game_id = ?"
						+ " AND member_id IN (" + placeholders(memberIds.size()) + ")",
				(rs, rowNum) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getBoolean(3) },
				args(playerId, gameId, memberIds));

		Set<Long> existingIds = existing.stream()
				.map(row -> (Long) row[0])
				.collect(Collectors.toCollection(LinkedHashSet::new));
		List<Long> reactivateIds = existing.stream()
				.filter(row -> "FAILED".equals(row[1])
						|| ("PENDING".equals(row[1]) && Boolean.TRUE.equals(row[2])))
				.map(row -> (Long) row[0])
				.toList();
		List<Long> newIds = memberIds.stream().filter(id -> !existingIds.contains(id)).toList();

		if (!newIds.isEmpty()) {
			jdbcTemplate.update(
					"INSERT IGNORE INTO player_solo_rank_push_delivery"
							+ " (member_id, player_id, game_id, status, created_at, updated_at) VALUES "
							+ newIds.stream()
									.map(id -> "(?, ?, ?, 'PENDING', NOW(), NOW())")
									.collect(Collectors.joining(", ")),
					newIds.stream()
							.flatMap(id -> List.of((Object) id, playerId, gameId).stream())
							.toArray());
		}
		if (!reactivateIds.isEmpty()) {
			jdbcTemplate.update(
					"UPDATE player_solo_rank_push_delivery"
							+ " SET status = 'PENDING', error_message = NULL, updated_at = NOW()"
							+ " WHERE player_id = ? AND game_id = ?"
							+ " AND member_id IN (" + placeholders(reactivateIds.size()) + ")",
					args(playerId, gameId, reactivateIds));
		}

		List<Long> targets = new ArrayList<>(newIds);
		targets.addAll(reactivateIds);
		return targets;
	}

	@Override
	public int markSentAll(Collection<Long> memberIds, Long playerId, String gameId) {
		int updated = 0;
		for (List<Long> chunk : chunk(memberIds)) {
			updated += jdbcTemplate.update(
					"UPDATE player_solo_rank_push_delivery"
							+ " SET status = 'SENT', error_message = NULL,"
							+ "     sent_at = NOW(), updated_at = NOW()"
							+ " WHERE player_id = ? AND game_id = ?"
							+ " AND member_id IN (" + placeholders(chunk.size()) + ")",
					args(playerId, gameId, chunk));
		}
		return updated;
	}

	@Override
	public int markFailedAll(
			Collection<Long> memberIds,
			Long playerId,
			String gameId,
			String errorMessage) {
		int updated = 0;
		for (List<Long> chunk : chunk(memberIds)) {
			List<Object> params = new ArrayList<>();
			params.add(errorMessage);
			params.add(playerId);
			params.add(gameId);
			params.addAll(chunk);
			updated += jdbcTemplate.update(
					"UPDATE player_solo_rank_push_delivery"
							+ " SET status = 'FAILED', error_message = ?, updated_at = NOW()"
							+ " WHERE player_id = ? AND game_id = ?"
							+ " AND member_id IN (" + placeholders(chunk.size()) + ")",
					params.toArray());
		}
		return updated;
	}

	private List<List<Long>> chunk(Collection<Long> memberIds) {
		if (memberIds == null || memberIds.isEmpty()) {
			return List.of();
		}
		List<Long> distinct = memberIds.stream().filter(Objects::nonNull).distinct().toList();
		List<List<Long>> chunks = new ArrayList<>();
		for (int start = 0; start < distinct.size(); start += CHUNK_SIZE) {
			chunks.add(distinct.subList(start, Math.min(start + CHUNK_SIZE, distinct.size())));
		}
		return chunks;
	}

	private String placeholders(int count) {
		return String.join(", ", Collections.nCopies(count, "?"));
	}

	private Object[] args(Long playerId, String gameId, List<Long> memberIds) {
		List<Object> params = new ArrayList<>();
		params.add(playerId);
		params.add(gameId);
		params.addAll(memberIds);
		return params.toArray();
	}
}
