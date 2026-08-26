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
}
