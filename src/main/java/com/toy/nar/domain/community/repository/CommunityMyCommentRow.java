package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;

/** "내가 쓴 댓글" 한 행. 원글 제목을 같이 실어 앱이 목록에서 바로 이동하게 한다. */
public record CommunityMyCommentRow(
		long id,
		long postId,
		String postTitle,
		String body,
		int likeCount,
		LocalDateTime createdAt) {
}
