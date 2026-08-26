package com.toy.nar.domain.community.repository;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.toy.nar.domain.community.entity.CommunityReport.TargetType;

import lombok.RequiredArgsConstructor;

/** 차단 쓰기와 신고 대상 검증·미리보기. 전부 유니크/PK 점조회라 엔티티가 과하다. */
@Repository
@RequiredArgsConstructor
public class CommunityModerationRepository {

	private final JdbcTemplate jdbcTemplate;

	/** @return 새로 차단했으면 true, 이미 차단 상태였으면 false (멱등). */
	public boolean insertBlock(long memberId, long blockedMemberId) {
		try {
			jdbcTemplate.update(
					"INSERT INTO member_block (member_id, blocked_member_id) VALUES (?, ?)",
					memberId, blockedMemberId);
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	/** @return 지운 게 있으면 true. 없어도 에러가 아니다 (멱등). */
	public boolean deleteBlock(long memberId, long blockedMemberId) {
		return jdbcTemplate.update(
				"DELETE FROM member_block WHERE member_id = ? AND blocked_member_id = ?",
				memberId, blockedMemberId) > 0;
	}

	/**
	 * 신고 대상이 존재하고 VISIBLE 이면 Discord 알림용 미리보기를 돌려준다.
	 * 비면 신고 불가(실존·상태 검증을 겸한다) — 다형 참조라 FK 가 없어 여기가 유일한 관문.
	 */
	public Optional<String> findVisibleTargetPreview(TargetType targetType, long targetId) {
		String sql = switch (targetType) {
			case POST -> "SELECT CONCAT(title, ' — ', LEFT(body, 80)) FROM community_post"
					+ " WHERE id = ? AND status = 'VISIBLE'";
			case COMMENT -> "SELECT LEFT(body, 80) FROM community_comment"
					+ " WHERE id = ? AND status = 'VISIBLE'";
			case IMAGE -> "SELECT image_url FROM community_post_image"
					+ " WHERE id = ? AND status = 'VISIBLE'";
		};
		return jdbcTemplate.query(sql, rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(),
				targetId);
	}
}
