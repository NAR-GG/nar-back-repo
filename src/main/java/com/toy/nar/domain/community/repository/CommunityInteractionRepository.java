package com.toy.nar.domain.community.repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요·스크랩 토글과 차단 목록 조회. 전부 유니크 인덱스 점조회/점변경이라 엔티티가 과하다.
 *
 * <p>토글은 "DELETE 먼저, 안 지워졌으면 INSERT" — 존재 확인과 해제를 한 문장으로 겸한다.
 * 동시 더블탭으로 둘 다 DELETE 0 → 둘 다 INSERT 가 되면 uk 가 한쪽을 막는데, 그
 * DuplicateKeyException 은 "이미 좋아요됨"이므로 삼키고 카운터 증감 없이 liked=true 로
 * 처리한다(호출부가 반환값 ALREADY 를 보고 판단).</p>
 */
@Repository
@RequiredArgsConstructor
public class CommunityInteractionRepository {

	/** 토글 결과. ADDED/REMOVED 만 카운터를 움직인다. */
	public enum ToggleResult {
		ADDED, REMOVED, ALREADY_ADDED
	}

	private final JdbcTemplate jdbcTemplate;

	/** 내가 차단한 회원 id 목록. uk_member_block (member_id, blocked_member_id) 커버링. */
	public List<Long> findBlockedMemberIds(long memberId) {
		return jdbcTemplate.queryForList(
				"SELECT blocked_member_id FROM member_block WHERE member_id = ?", Long.class, memberId);
	}

	public ToggleResult togglePostLike(long postId, long memberId) {
		return toggle("community_post_like", "post_id", postId, memberId);
	}

	public ToggleResult toggleCommentLike(long commentId, long memberId) {
		return toggle("community_comment_like", "comment_id", commentId, memberId);
	}

	public ToggleResult toggleScrap(long postId, long memberId) {
		return toggle("community_scrap", "post_id", postId, memberId);
	}

	/** 글 단위 알림 mute 토글. ADDED = 방금 껐다(mute 행 생성), REMOVED = 다시 켰다. */
	public ToggleResult toggleNotificationMute(long postId, long memberId) {
		return toggle("community_post_notification_mute", "post_id", postId, memberId);
	}

	/** 이 회원이 이 글의 알림을 꺼뒀는지 — 발송 판정·상세 viewer 용 uk 점조회. */
	public boolean isNotificationMuted(long postId, long memberId) {
		return exists("community_post_notification_mute", "post_id", postId, memberId);
	}

	public boolean existsPostLike(long postId, long memberId) {
		return exists("community_post_like", "post_id", postId, memberId);
	}

	public boolean existsScrap(long postId, long memberId) {
		return exists("community_scrap", "post_id", postId, memberId);
	}

	/** 이 페이지의 댓글들 중 내가 좋아요한 것. uk (comment_id, member_id) 점조회 묶음. */
	public Set<Long> findLikedCommentIds(long memberId, List<Long> commentIds) {
		if (commentIds.isEmpty()) {
			return Set.of();
		}
		String placeholders = String.join(", ", java.util.Collections.nCopies(commentIds.size(), "?"));
		List<Object> params = new java.util.ArrayList<>(commentIds);
		params.add(memberId);
		return new HashSet<>(jdbcTemplate.queryForList(
				"SELECT comment_id FROM community_comment_like WHERE comment_id IN (" + placeholders
						+ ") AND member_id = ?",
				Long.class, params.toArray()));
	}

	private ToggleResult toggle(String table, String targetColumn, long targetId, long memberId) {
		int deleted = jdbcTemplate.update(
				"DELETE FROM " + table + " WHERE " + targetColumn + " = ? AND member_id = ?", targetId, memberId);
		if (deleted > 0) {
			return ToggleResult.REMOVED;
		}
		try {
			jdbcTemplate.update(
					"INSERT INTO " + table + " (" + targetColumn + ", member_id) VALUES (?, ?)", targetId, memberId);
			return ToggleResult.ADDED;
		} catch (DuplicateKeyException e) {
			return ToggleResult.ALREADY_ADDED;
		}
	}

	private boolean exists(String table, String targetColumn, long targetId, long memberId) {
		Integer found = jdbcTemplate.query(
				"SELECT 1 FROM " + table + " WHERE " + targetColumn + " = ? AND member_id = ? LIMIT 1",
				rs -> rs.next() ? 1 : null, targetId, memberId);
		return found != null;
	}
}
