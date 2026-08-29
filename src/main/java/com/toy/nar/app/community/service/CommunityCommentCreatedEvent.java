package com.toy.nar.app.community.service;

/**
 * 댓글 저장 커밋 후 알림 발송용. 수신 후보 둘 —
 * postAuthorId(내 글에 댓글), replyTargetId(내 댓글에 답글, 멘션 대상과 동일 인물).
 * 리스너가 자기 자신·차단·중복(둘이 같은 사람)을 거른다.
 */
public record CommunityCommentCreatedEvent(
		long postId,
		long commentId,
		long authorId,
		String authorNickname,
		Long postAuthorId,
		Long replyTargetId,
		String bodyPreview) {
}
