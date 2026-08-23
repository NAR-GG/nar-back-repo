package com.toy.nar.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class LiveActivityCardDispatchRepositoryImpl implements LiveActivityCardDispatchRepository {

	/** IN 절 한 번에 넣을 최대 개수. 발송 대상이 3천 명대라 SQL 길이를 나눈다. */
	private static final int CHUNK_SIZE = 500;

	private final JdbcTemplate jdbcTemplate;

	@Override
	public List<Long> claimAll(Collection<Long> memberIds, String matchId, int setNumber) {
		if (memberIds == null || memberIds.isEmpty() || matchId == null || matchId.isBlank()) {
			return List.of();
		}
		List<Long> claimed = new ArrayList<>();
		for (List<Long> chunk : chunk(memberIds)) {
			claimed.addAll(claimChunk(chunk, matchId, setNumber));
		}
		return claimed;
	}

	/**
	 * 기존 행을 먼저 읽고 없는 것만 INSERT IGNORE 한다 — 팬아웃 예약({@code reserveAll})과 같은 모양이다.
	 *
	 * <p>ponytail: SELECT 와 INSERT 사이에 다른 파드가 같은 회원을 넣으면 이쪽 INSERT 가 무시되지만
	 * 우리는 선점한 것으로 보고 발송한다 — 그 회원에게 카드가 두 장 갈 수 있다. 그 창은 스케줄러
	 * 롤아웃으로 파드가 순간 겹치는 몇 초뿐이고(평상시 스케줄러는 하나), 대가는 카드 한 장이다.
	 * 완전히 없애려면 회원 단위로 INSERT IGNORE 후 갱신 건수를 보면 되는데, 3천 명이면 왕복이
	 * 3천 번이라 팬아웃이 느려진 원인(2026-07-30, 37초)을 그대로 되풀이한다.</p>
	 */
	private List<Long> claimChunk(List<Long> memberIds, String matchId, int setNumber) {
		String placeholders = placeholders(memberIds.size());

		List<Long> existing = jdbcTemplate.query(
				"SELECT member_id FROM live_activity_card_dispatch"
						+ " WHERE match_id = ? AND member_id IN (" + placeholders + ")",
				(rs, rowNum) -> rs.getLong(1),
				args(matchId, memberIds));
		Set<Long> existingIds = new LinkedHashSet<>(existing);

		List<Long> newIds = memberIds.stream().filter(id -> id != null && !existingIds.contains(id)).toList();
		if (newIds.isEmpty()) {
			return List.of();
		}

		StringBuilder sql = new StringBuilder(
				"INSERT IGNORE INTO live_activity_card_dispatch (member_id, match_id, set_number) VALUES ");
		for (int i = 0; i < newIds.size(); i++) {
			sql.append(i == 0 ? "" : ", ").append("(?, ?, ?)");
		}
		Object[] params = new Object[newIds.size() * 3];
		for (int i = 0; i < newIds.size(); i++) {
			params[i * 3] = newIds.get(i);
			params[i * 3 + 1] = matchId;
			params[i * 3 + 2] = setNumber;
		}
		jdbcTemplate.update(sql.toString(), params);
		return newIds;
	}

	@Override
	public int release(Long memberId, String matchId, java.time.Duration staleAfter) {
		if (memberId == null || matchId == null || matchId.isBlank() || staleAfter == null) {
			return 0;
		}
		// 나이 판정을 DB 시계로 한다 — 파드마다 다른 인메모리 창을 쓰던 예전 구조가 #442 이후
		// 세트 시작(스케줄러)과 따라잡기(웹) 사이에서 공유되지 않던 원인이었다.
		return jdbcTemplate.update(
				"DELETE FROM live_activity_card_dispatch"
						+ " WHERE member_id = ? AND match_id = ?"
						+ " AND created_at < DATE_SUB(NOW(), INTERVAL ? SECOND)",
				memberId, matchId, Math.max(0, staleAfter.toSeconds()));
	}

	@Override
	public int deleteAllByMatchId(String matchId) {
		if (matchId == null || matchId.isBlank()) {
			return 0;
		}
		return jdbcTemplate.update(
				"DELETE FROM live_activity_card_dispatch WHERE match_id = ?", matchId);
	}

	private List<List<Long>> chunk(Collection<Long> memberIds) {
		List<Long> all = new ArrayList<>(new LinkedHashSet<>(memberIds));
		List<List<Long>> chunks = new ArrayList<>();
		for (int from = 0; from < all.size(); from += CHUNK_SIZE) {
			chunks.add(all.subList(from, Math.min(from + CHUNK_SIZE, all.size())));
		}
		return chunks;
	}

	private static String placeholders(int size) {
		return String.join(", ", java.util.Collections.nCopies(size, "?"));
	}

	private static Object[] args(String matchId, List<Long> memberIds) {
		Object[] args = new Object[1 + memberIds.size()];
		args[0] = matchId;
		for (int i = 0; i < memberIds.size(); i++) {
			args[1 + i] = memberIds.get(i);
		}
		return args;
	}
}
