package com.toy.nar.app.community.service;

/**
 * 좋아요 커밋 후 알림 발송용. ADDED(새로 눌림)일 때만 발행된다 — 취소·중복은
 * 발행 지점(toggleLike)에서 걸러진다. (글 × 누른 사람) 최초 1회 dedupe 는
 * 리스너가 community_like_notification 으로 판정한다.
 */
public record CommunityPostLikedEvent(
		long postId,
		long actorId,
		String actorNickname,
		Long postAuthorId,
		String postTitle) {
}
