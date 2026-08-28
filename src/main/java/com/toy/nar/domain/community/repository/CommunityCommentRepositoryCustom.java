package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunityCommentRepositoryCustom {

	/** 댓글 한 페이지. 오래된 순(id ASC), 커서는 마지막 id — id > cursor 로 이어 읽는다. */
	List<CommunityCommentRow> findPage(long postId, Long cursorId, int size);

	/** 작성 간격 검사용 마지막 작성 시각. status 무관. */
	Optional<LocalDateTime> findLastCreatedAt(long memberId);

	/** like_count 원자 증감 후 갱신된 값을 돌려준다. */
	int applyLikeDelta(long commentId, int delta);

	/**
	 * 내가 쓴 댓글. 커서는 comment.id DESC. 삭제·블라인드 댓글과 원글이 내려간 댓글은
	 * 숨긴다 — 원글 상세로 이동할 수 없는 행은 보여줘도 갈 곳이 없다.
	 * member_id 는 FK 자동 인덱스(fk_community_comment_member)가 커버한다.
	 */
	List<CommunityMyCommentRow> findMyCommentPage(long memberId, Long cursorId, int size);
}
