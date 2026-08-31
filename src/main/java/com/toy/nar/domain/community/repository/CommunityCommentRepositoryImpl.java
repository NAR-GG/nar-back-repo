package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import lombok.RequiredArgsConstructor;

/**
 * 댓글 목록 SQL. 작성자와 멘션 대상을 member 이중 LEFT JOIN 으로 한 방에 붙인다 —
 * 페이지당 수십 행이라 조인 비용은 무시 가능하고, 닉네임은 조회 시점 값이라 변경이 자동 반영된다.
 * 삭제·차단 댓글도 행을 내려보낸다(자리 유지, D-5) — 마스킹은 서비스가 한다.
 */
@RequiredArgsConstructor
public class CommunityCommentRepositoryImpl implements CommunityCommentRepositoryCustom {

	private static final RowMapper<CommunityCommentRow> ROW_MAPPER = (rs, i) -> new CommunityCommentRow(
			rs.getLong("id"),
			rs.getObject("parent_id", Long.class),
			rs.getString("body"),
			rs.getString("status"),
			rs.getInt("like_count"),
			rs.getTimestamp("created_at").toLocalDateTime(),
			rs.getTimestamp("edited_at") == null ? null : rs.getTimestamp("edited_at").toLocalDateTime(),
			rs.getObject("author_member_id", Long.class),
			rs.getString("author_name"),
			rs.getString("author_tag"),
			rs.getString("author_profile_image_url"),
			rs.getObject("author_team_id", Long.class),
			rs.getString("author_team_code"),
			rs.getString("author_team_image_url"),
			rs.getObject("mention_member_id", Long.class),
			rs.getString("mention_name"),
			rs.getString("mention_tag"));

	private final JdbcTemplate jdbcTemplate;

	@Override
	public List<CommunityCommentRow> findPage(long postId, Long cursorId, int size) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.id, c.parent_id, c.body, c.status, c.like_count, c.created_at, c.edited_at,
				       c.member_id AS author_member_id, m.name AS author_name, m.tag AS author_tag,
				       m.profile_image_url AS author_profile_image_url,
				       t.team_id AS author_team_id, t.team_code AS author_team_code,
				       t.team_image_url AS author_team_image_url,
				       c.mention_member_id, mm.name AS mention_name, mm.tag AS mention_tag
				FROM community_comment c
				LEFT JOIN member m ON m.id = c.member_id
				LEFT JOIN teams t ON t.team_id = c.author_team_id
				LEFT JOIN member mm ON mm.id = c.mention_member_id
				WHERE c.post_id = ?
				""");
		List<Object> params = new ArrayList<>();
		params.add(postId);
		if (cursorId != null) {
			sql.append("  AND c.id > ?\n");
			params.add(cursorId);
		}
		sql.append("ORDER BY c.id LIMIT ?");
		params.add(size);

		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
	}

	@Override
	public List<CommunityMyCommentRow> findMyCommentPage(long memberId, Long cursorId, int size) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.id, c.post_id, p.title AS post_title,
				       p.board_team_id, bt.team_code AS board_team_code,
				       c.body, c.like_count, c.created_at, c.edited_at
				FROM community_comment c
				JOIN community_post p ON p.id = c.post_id
				LEFT JOIN teams bt ON bt.team_id = p.board_team_id
				WHERE c.member_id = ?
				  AND c.status = 'VISIBLE'
				  -- TEST 글의 댓글도 내 활동에 남긴다. TEST 글은 테스터만 볼 수 있어
				  -- 거기에 댓글을 달 수 있는 사람도 테스터뿐이라 노출 위험이 없다.
				  AND p.status IN ('VISIBLE', 'TEST')
				""");
		List<Object> params = new ArrayList<>();
		params.add(memberId);
		if (cursorId != null) {
			sql.append("  AND c.id < ?\n");
			params.add(cursorId);
		}
		sql.append("ORDER BY c.id DESC LIMIT ?");
		params.add(size);
		return jdbcTemplate.query(sql.toString(),
				(rs, i) -> new CommunityMyCommentRow(
						rs.getLong("id"),
						rs.getLong("post_id"),
						rs.getString("post_title"),
						rs.getObject("board_team_id", Long.class),
						rs.getString("board_team_code"),
						rs.getString("body"),
						rs.getInt("like_count"),
						rs.getTimestamp("created_at").toLocalDateTime(),
						rs.getTimestamp("edited_at") == null ? null
								: rs.getTimestamp("edited_at").toLocalDateTime()),
				params.toArray());
	}

	@Override
	public Optional<LocalDateTime> findLastCreatedAt(long memberId) {
		List<LocalDateTime> result = jdbcTemplate.query(
				"SELECT created_at FROM community_comment WHERE member_id = ? ORDER BY id DESC LIMIT 1",
				(rs, i) -> rs.getTimestamp(1).toLocalDateTime(), memberId);
		return result.stream().findFirst();
	}

	@Override
	public int applyLikeDelta(long commentId, int delta) {
		jdbcTemplate.update(
				"UPDATE community_comment SET like_count = GREATEST(0, like_count + ?) WHERE id = ?",
				delta, commentId);
		Integer count = jdbcTemplate.queryForObject(
				"SELECT like_count FROM community_comment WHERE id = ?", Integer.class, commentId);
		return count == null ? 0 : count;
	}
}
