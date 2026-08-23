package com.toy.nar.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LiveActivityMatchProgressRepositoryImpl implements LiveActivityMatchProgressRepository {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public Optional<Long> findProgressKey(String matchId) {
		if (blank(matchId)) {
			return Optional.empty();
		}
		return jdbcTemplate.query(
						"SELECT progress_key FROM live_activity_match_progress WHERE match_id = ?",
						(rs, rowNum) -> rs.getLong(1), matchId)
				.stream().findFirst();
	}

	@Override
	public void raiseProgressKey(String matchId, long progressKey) {
		if (blank(matchId)) {
			return;
		}
		jdbcTemplate.update(
				"INSERT INTO live_activity_match_progress (match_id, progress_key) VALUES (?, ?)"
						+ " ON DUPLICATE KEY UPDATE progress_key = GREATEST(progress_key, VALUES(progress_key))",
				matchId, progressKey);
	}

	@Override
	public boolean isMatchEndPushed(String matchId) {
		if (blank(matchId)) {
			return false;
		}
		return !jdbcTemplate.query(
				"SELECT 1 FROM live_activity_match_progress"
						+ " WHERE match_id = ? AND match_end_pushed_at IS NOT NULL",
				(rs, rowNum) -> 1, matchId).isEmpty();
	}

	@Override
	public boolean claimMatchEndPush(String matchId) {
		if (blank(matchId)) {
			return false;
		}
		// 행이 없을 수도 있다(세트 갱신 없이 곧바로 종료 발송이 오는 경로). 먼저 만들어 둔다.
		// progress_key 0 은 어떤 실제 키보다 작으므로 워터마크에 영향을 주지 않는다.
		jdbcTemplate.update(
				"INSERT IGNORE INTO live_activity_match_progress (match_id, progress_key) VALUES (?, 0)",
				matchId);
		// IS NULL 조건이 선점이다. 두 발송자가 동시에 들어와도 한쪽만 1을 받는다.
		return jdbcTemplate.update(
				"UPDATE live_activity_match_progress SET match_end_pushed_at = NOW()"
						+ " WHERE match_id = ? AND match_end_pushed_at IS NULL",
				matchId) == 1;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
