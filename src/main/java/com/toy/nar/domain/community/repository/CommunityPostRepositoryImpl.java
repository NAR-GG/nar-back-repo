package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import lombok.RequiredArgsConstructor;

/**
 * 목록·상세·카운터 SQL. 쿼리 수칙(설계 문서 7장):
 * - {@code <=>} 를 쓰지 않고 전체/팀 게시판 조건을 문자열로 분기한다 — NULL 파라미터 섞인
 *   {@code <=>} 는 옵티마이저가 range 를 못 잡는 경우가 있다.
 * - 차단 필터도 목록 유무로 동적 조립. 차단 목록은 수십 개 수준의 리터럴 IN 이다.
 * - 정렬·커서는 (board_team_id, id DESC) 인덱스가 동시에 처리한다.
 */
@RequiredArgsConstructor
public class CommunityPostRepositoryImpl implements CommunityPostRepositoryCustom {

	private static final String SELECT_COLUMNS = """
			SELECT p.id, p.board_team_id, bt.team_code AS board_team_code,
			       p.title, p.body, p.body_format, p.preview, p.view_count, p.like_count,
			       p.comment_count, p.status, p.created_at, p.edited_at,
			       p.member_id AS author_member_id, m.name AS author_name, m.tag AS author_tag,
			       m.profile_image_url AS author_profile_image_url,
			       t.team_id AS author_team_id, t.team_code AS author_team_code,
			       t.team_image_url AS author_team_image_url,
			       %s AS scrap_id
			FROM community_post p
			LEFT JOIN member m ON m.id = p.member_id
			LEFT JOIN teams t ON t.team_id = p.author_team_id
			LEFT JOIN teams bt ON bt.team_id = p.board_team_id
			""";

	private static final RowMapper<CommunityPostRow> ROW_MAPPER = (rs, i) -> new CommunityPostRow(
			rs.getLong("id"),
			rs.getObject("board_team_id", Long.class),
			rs.getString("board_team_code"),
			rs.getString("title"),
			rs.getString("body"),
			rs.getString("body_format"),
			rs.getString("preview"),
			rs.getInt("view_count"),
			rs.getInt("like_count"),
			rs.getInt("comment_count"),
			rs.getString("status"),
			rs.getTimestamp("created_at").toLocalDateTime(),
			rs.getTimestamp("edited_at") == null ? null : rs.getTimestamp("edited_at").toLocalDateTime(),
			rs.getObject("author_member_id", Long.class),
			rs.getString("author_name"),
			rs.getString("author_tag"),
			rs.getString("author_profile_image_url"),
			rs.getObject("author_team_id", Long.class),
			rs.getString("author_team_code"),
			rs.getString("author_team_image_url"),
			rs.getObject("scrap_id", Long.class));

	private final JdbcTemplate jdbcTemplate;

	@Override
	public List<CommunityPostRow> findPage(Long boardTeamId, Long cursorId, List<Long> excludedMemberIds, int size) {
		StringBuilder sql = new StringBuilder(SELECT_COLUMNS.formatted("NULL"));
		List<Object> params = new ArrayList<>();

		if (boardTeamId == null) {
			sql.append("WHERE p.board_team_id IS NULL\n");
		} else {
			sql.append("WHERE p.board_team_id = ?\n");
			params.add(boardTeamId);
		}
		sql.append("  AND p.status = 'VISIBLE'\n");
		if (cursorId != null) {
			sql.append("  AND p.id < ?\n");
			params.add(cursorId);
		}
		appendExcludedAuthors(sql, params, excludedMemberIds);
		sql.append("ORDER BY p.id DESC LIMIT ?");
		params.add(size);

		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
	}

	@Override
	public Optional<CommunityPostRow> findRowById(long postId) {
		List<CommunityPostRow> rows = jdbcTemplate.query(
				SELECT_COLUMNS.formatted("NULL") + "WHERE p.id = ?", ROW_MAPPER, postId);
		return rows.stream().findFirst();
	}

	@Override
	public List<CommunityPostRow> findScrapPage(long memberId, Long cursorId, int size) {
		// idx_community_scrap_member (member_id, id DESC, post_id) 커버링으로 스크랩을 훑고
		// post 는 PK 조인. 숨김·삭제된 글은 스크랩 목록에서도 뺀다.
		StringBuilder sql = new StringBuilder(SELECT_COLUMNS.formatted("s.id")
				.replace("FROM community_post p",
						"FROM community_scrap s JOIN community_post p ON p.id = s.post_id"));
		List<Object> params = new ArrayList<>();
		sql.append("WHERE s.member_id = ?\n");
		params.add(memberId);
		if (cursorId != null) {
			sql.append("  AND s.id < ?\n");
			params.add(cursorId);
		}
		sql.append("  AND p.status = 'VISIBLE'\n");
		sql.append("ORDER BY s.id DESC LIMIT ?");
		params.add(size);

		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
	}

	@Override
	public List<CommunityPostRow> findMyPostPage(long memberId, Long cursorId, int size) {
		// idx_community_post_member (member_id, id DESC) 가 필터·정렬·커서를 같이 처리한다.
		StringBuilder sql = new StringBuilder(SELECT_COLUMNS.formatted("NULL"));
		List<Object> params = new ArrayList<>();
		sql.append("WHERE p.member_id = ?\n");
		params.add(memberId);
		sql.append("  AND p.status = 'VISIBLE'\n");
		if (cursorId != null) {
			sql.append("  AND p.id < ?\n");
			params.add(cursorId);
		}
		sql.append("ORDER BY p.id DESC LIMIT ?");
		params.add(size);
		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
	}

	@Override
	public List<CommunityPostRow> findLikedPage(long memberId, Long cursorId, int size) {
		// fk_community_post_like_member (member_id) 자동 인덱스가 실질 (member_id, id) 로
		// 필터·정렬·커서를 처리한다(#490). 커서는 like.id — scrap_id 슬롯에 실린다.
		StringBuilder sql = new StringBuilder(SELECT_COLUMNS.formatted("l.id")
				.replace("FROM community_post p",
						"FROM community_post_like l JOIN community_post p ON p.id = l.post_id"));
		List<Object> params = new ArrayList<>();
		sql.append("WHERE l.member_id = ?\n");
		params.add(memberId);
		if (cursorId != null) {
			sql.append("  AND l.id < ?\n");
			params.add(cursorId);
		}
		sql.append("  AND p.status = 'VISIBLE'\n");
		sql.append("ORDER BY l.id DESC LIMIT ?");
		params.add(size);
		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
	}

	@Override
	public Optional<LocalDateTime> findLastCreatedAt(long memberId, Long boardTeamId) {
		// idx_community_post_member_board (member_id, board_team_id, id DESC) 최신 1행.
		// status 무관 — 지웠다 다시 올리는 우회도 잡는다.
		//
		// 전체 게시판은 board_team_id 가 NULL 이라 `= ?` 로는 절대 안 맞는다.
		// NULL-safe 비교(<=>)를 써야 한 문장으로 두 경우를 다 덮는다.
		List<LocalDateTime> result = jdbcTemplate.query(
				"SELECT created_at FROM community_post"
						+ " WHERE member_id = ? AND board_team_id <=> ? ORDER BY id DESC LIMIT 1",
				(rs, i) -> rs.getTimestamp(1).toLocalDateTime(), memberId, boardTeamId);
		return result.stream().findFirst();
	}

	@Override
	public void increaseViewCount(long postId) {
		jdbcTemplate.update("UPDATE community_post SET view_count = view_count + 1 WHERE id = ?", postId);
	}

	@Override
	public int applyLikeDelta(long postId, int delta) {
		jdbcTemplate.update(
				"UPDATE community_post SET like_count = GREATEST(0, like_count + ?) WHERE id = ?", delta, postId);
		Integer count = jdbcTemplate.queryForObject(
				"SELECT like_count FROM community_post WHERE id = ?", Integer.class, postId);
		return count == null ? 0 : count;
	}

	@Override
	public void applyCommentDelta(long postId, int delta) {
		jdbcTemplate.update(
				"UPDATE community_post SET comment_count = GREATEST(0, comment_count + ?) WHERE id = ?",
				delta, postId);
	}

	@Override
	public void replaceImages(long postId, List<String> imageUrls) {
		jdbcTemplate.update("DELETE FROM community_post_image WHERE post_id = ?", postId);
		if (imageUrls == null || imageUrls.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
				"INSERT INTO community_post_image (post_id, image_url, sort_order) VALUES (?, ?, ?)",
				new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
					@Override
					public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
						ps.setLong(1, postId);
						ps.setString(2, imageUrls.get(i));
						ps.setInt(3, i);
					}

					@Override
					public int getBatchSize() {
						return imageUrls.size();
					}
				});
	}

	@Override
	public List<CommunityPostImageRow> findVisibleImages(long postId) {
		return jdbcTemplate.query(
				"SELECT id, post_id, image_url FROM community_post_image"
						+ " WHERE post_id = ? AND status = 'VISIBLE' ORDER BY sort_order",
				(rs, i) -> new CommunityPostImageRow(rs.getLong(1), rs.getLong(2), rs.getString(3)),
				postId);
	}

	@Override
	public List<CommunityPostImageRow> findVisibleImagesByPostIds(List<Long> postIds) {
		if (postIds == null || postIds.isEmpty()) {
			return List.of();
		}
		String placeholders = String.join(", ", java.util.Collections.nCopies(postIds.size(), "?"));
		return jdbcTemplate.query(
				"SELECT id, post_id, image_url FROM community_post_image"
						+ " WHERE post_id IN (" + placeholders + ") AND status = 'VISIBLE'"
						+ " ORDER BY post_id, sort_order",
				(rs, i) -> new CommunityPostImageRow(rs.getLong(1), rs.getLong(2), rs.getString(3)),
				postIds.toArray());
	}

	static void appendExcludedAuthors(StringBuilder sql, List<Object> params, List<Long> excludedMemberIds) {
		if (excludedMemberIds == null || excludedMemberIds.isEmpty()) {
			return;
		}
		// 작성자가 탈퇴(SET NULL)한 행은 차단 대상일 수 없으므로 IS NULL 을 같이 열어 둔다.
		String placeholders = String.join(", ", java.util.Collections.nCopies(excludedMemberIds.size(), "?"));
		sql.append("  AND (p.member_id IS NULL OR p.member_id NOT IN (").append(placeholders).append("))\n");
		params.addAll(excludedMemberIds);
	}
}
