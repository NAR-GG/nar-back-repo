package com.toy.nar.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class MemberTeamEventPushDeliveryRepositoryImpl
		implements MemberTeamEventPushDeliveryRepositoryCustom {

	/** IN 절 한 번에 넣을 최대 개수. SQL 이 과하게 길어지지 않게 나눈다. */
	private static final int CHUNK_SIZE = 500;

	/** PENDING 방치 재예약 기준. 단건 reserve 의 reactivateStale 과 같은 값이어야 한다. */
	private static final String STALE_PENDING_MINUTES = "1";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public List<Long> reserveAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder) {
		List<Long> targets = new ArrayList<>();
		for (List<Long> chunk : chunk(memberIds)) {
			targets.addAll(reserveChunk(chunk, matchId, setNumber, eventType, eventOrder));
		}
		return targets;
	}

	private List<Long> reserveChunk(
			List<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder) {
		String placeholders = placeholders(memberIds.size());

		// 1) 기존 행을 한 번에 읽어 신규/재예약/제외를 자바에서 가른다.
		//    단건 reserve 의 "INSERT IGNORE 실패 → 조건부 UPDATE" 순서를 그대로 재현한다.
		List<Object[]> existing = jdbcTemplate.query(
				"SELECT member_id, status, updated_at < DATE_SUB(NOW(), INTERVAL "
						+ STALE_PENDING_MINUTES + " MINUTE) AS stale"
						+ " FROM member_team_event_push_delivery"
						+ " WHERE match_id = ? AND set_number = ? AND event_type = ? AND event_order = ?"
						+ " AND member_id IN (" + placeholders + ")",
				(rs, rowNum) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getBoolean(3) },
				args(matchId, setNumber, eventType, eventOrder, memberIds));

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
					"INSERT IGNORE INTO member_team_event_push_delivery"
							+ " (member_id, match_id, set_number, event_type, event_order,"
							+ "  status, created_at, updated_at) VALUES "
							+ newIds.stream()
									.map(id -> "(?, ?, ?, ?, ?, 'PENDING', NOW(), NOW())")
									.collect(Collectors.joining(", ")),
					newIds.stream()
							.flatMap(id -> List.of(
									(Object) id, matchId, setNumber, eventType, eventOrder).stream())
							.toArray());
		}
		if (!reactivateIds.isEmpty()) {
			jdbcTemplate.update(
					"UPDATE member_team_event_push_delivery"
							+ " SET status = 'PENDING', error_message = NULL, updated_at = NOW()"
							+ " WHERE match_id = ? AND set_number = ? AND event_type = ? AND event_order = ?"
							+ " AND member_id IN (" + placeholders(reactivateIds.size()) + ")",
					args(matchId, setNumber, eventType, eventOrder, reactivateIds));
		}

		List<Long> targets = new ArrayList<>(newIds);
		targets.addAll(reactivateIds);
		return targets;
	}

	@Override
	public int markSentAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder) {
		int updated = 0;
		for (List<Long> chunk : chunk(memberIds)) {
			updated += jdbcTemplate.update(
					"UPDATE member_team_event_push_delivery"
							+ " SET status = 'SENT', error_message = NULL,"
							+ "     sent_at = NOW(), updated_at = NOW()"
							+ " WHERE match_id = ? AND set_number = ? AND event_type = ? AND event_order = ?"
							+ " AND member_id IN (" + placeholders(chunk.size()) + ")",
					args(matchId, setNumber, eventType, eventOrder, chunk));
		}
		return updated;
	}

	@Override
	public int markFailedAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder,
			String errorMessage) {
		int updated = 0;
		for (List<Long> chunk : chunk(memberIds)) {
			List<Object> params = new ArrayList<>();
			params.add(errorMessage);
			params.add(matchId);
			params.add(setNumber);
			params.add(eventType);
			params.add(eventOrder);
			params.addAll(chunk);
			updated += jdbcTemplate.update(
					"UPDATE member_team_event_push_delivery"
							+ " SET status = 'FAILED', error_message = ?, updated_at = NOW()"
							+ " WHERE match_id = ? AND set_number = ? AND event_type = ? AND event_order = ?"
							+ " AND member_id IN (" + placeholders(chunk.size()) + ")",
					params.toArray());
		}
		return updated;
	}

	private List<List<Long>> chunk(Collection<Long> memberIds) {
		List<Long> distinct = memberIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		List<List<Long>> chunks = new ArrayList<>();
		for (int start = 0; start < distinct.size(); start += CHUNK_SIZE) {
			chunks.add(distinct.subList(start, Math.min(start + CHUNK_SIZE, distinct.size())));
		}
		return chunks;
	}

	private String placeholders(int count) {
		return String.join(", ", java.util.Collections.nCopies(count, "?"));
	}

	private Object[] args(String matchId, int setNumber, String eventType, long eventOrder, List<Long> memberIds) {
		List<Object> params = new ArrayList<>();
		params.add(matchId);
		params.add(setNumber);
		params.add(eventType);
		params.add(eventOrder);
		params.addAll(memberIds);
		return params.toArray();
	}
}
