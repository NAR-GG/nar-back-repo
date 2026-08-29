package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;

/** 댓글 목록 조인 결과 한 행. 작성자·멘션 대상은 LEFT JOIN 이라 null 가능. */
public record CommunityCommentRow(
		long id,
		Long parentId,
		String body,
		String status,
		int likeCount,
		LocalDateTime createdAt,
		LocalDateTime editedAt,
		Long authorMemberId,
		String authorName,
		String authorTag,
		String authorProfileImageUrl,
		Long authorTeamId,
		String authorTeamCode,
		String authorTeamImageUrl,
		Long mentionMemberId,
		String mentionName,
		String mentionTag) {
}
