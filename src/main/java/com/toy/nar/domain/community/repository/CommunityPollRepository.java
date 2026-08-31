package com.toy.nar.domain.community.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 투표(글당 1개, 단일 선택) SQL. 스키마는 V79 — uk(post_id) 가 글당 1개를,
 * uk(poll_id, member_id) 가 1인 1표를 DB 레벨에서 보장한다.
 *
 * <p>카운터(total_votes, vote_count)는 커뮤니티 수칙대로 원자 UPDATE 로만 만진다.
 * 회원 하드 삭제 시 vote 행은 CASCADE 로 사라지지만 카운터는 안 줄어든다 —
 * 지금 규모에서 감수(설계 문서 6단계).</p>
 */
@Repository
@RequiredArgsConstructor
public class CommunityPollRepository {

	public record PollRow(long id, long postId, String question, boolean hideResultsUntilVoted, int totalVotes) {
	}

	public record PollOptionRow(long id, String label, int voteCount) {
	}

	private final JdbcTemplate jdbcTemplate;

	/** 투표 + 선택지 생성(글 작성 트랜잭션 안에서 호출). */
	public void createPoll(long postId, String question, List<String> options) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO community_poll (post_id, question) VALUES (?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, postId);
			ps.setString(2, question);
			return ps;
		}, keyHolder);
		long pollId = keyHolder.getKey().longValue();
		jdbcTemplate.batchUpdate(
				"INSERT INTO community_poll_option (poll_id, label, sort_order) VALUES (?, ?, ?)",
				new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
						ps.setLong(1, pollId);
						ps.setString(2, options.get(i));
						ps.setInt(3, i); // 같은 라벨이 두 개여도 순서가 안 겹치게 인덱스로
					}

					@Override
					public int getBatchSize() {
						return options.size();
					}
				});
	}

	public Optional<PollRow> findByPostId(long postId) {
		return jdbcTemplate.query(
				"SELECT id, post_id, question, hide_results_until_voted, total_votes"
						+ " FROM community_poll WHERE post_id = ?",
				(rs, i) -> new PollRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
						rs.getBoolean(4), rs.getInt(5)),
				postId).stream().findFirst();
	}

	public List<PollOptionRow> findOptions(long pollId) {
		return jdbcTemplate.query(
				"SELECT id, label, vote_count FROM community_poll_option"
						+ " WHERE poll_id = ? ORDER BY sort_order",
				(rs, i) -> new PollOptionRow(rs.getLong(1), rs.getString(2), rs.getInt(3)),
				pollId);
	}

	/** 이 회원이 고른 선택지 id. 안 골랐으면 null. uk(poll_id, member_id) 점조회. */
	public Long findMyOptionId(long pollId, long memberId) {
		List<Long> result = jdbcTemplate.queryForList(
				"SELECT option_id FROM community_poll_vote WHERE poll_id = ? AND member_id = ?",
				Long.class, pollId, memberId);
		return result.isEmpty() ? null : result.get(0);
	}

	public boolean optionBelongsToPoll(long optionId, long pollId) {
		Integer found = jdbcTemplate.query(
				"SELECT 1 FROM community_poll_option WHERE id = ? AND poll_id = ? LIMIT 1",
				rs -> rs.next() ? 1 : null, optionId, pollId);
		return found != null;
	}

	/** 1표 기록. 이미 투표했으면 false — uk 가 최종 방어선이라 레이스에도 안전하다. */
	public boolean insertVote(long pollId, long optionId, long memberId) {
		try {
			jdbcTemplate.update(
					"INSERT INTO community_poll_vote (poll_id, option_id, member_id) VALUES (?, ?, ?)",
					pollId, optionId, memberId);
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	public void applyVote(long pollId, long optionId) {
		jdbcTemplate.update(
				"UPDATE community_poll SET total_votes = total_votes + 1 WHERE id = ?", pollId);
		jdbcTemplate.update(
				"UPDATE community_poll_option SET vote_count = vote_count + 1 WHERE id = ?", optionId);
	}
}
