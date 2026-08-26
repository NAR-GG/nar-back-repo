package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunityPostRepositoryCustom {

	/**
	 * 게시판 목록 한 페이지. 커서(id DESC) 기반이라 몇 페이지든 비용이 같다.
	 *
	 * @param boardTeamId       null 이면 전체 게시판(board_team_id IS NULL)
	 * @param cursorId          null 이면 첫 페이지, 값이 있으면 id < cursor
	 * @param excludedMemberIds 차단한 작성자들. 비어 있으면 필터 자체를 안 넣는다
	 */
	List<CommunityPostRow> findPage(Long boardTeamId, Long cursorId, List<Long> excludedMemberIds, int size);

	Optional<CommunityPostRow> findRowById(long postId);

	/** 내 스크랩 페이지. 커서는 scrap.id (row 의 scrapId 로 내려간다). */
	List<CommunityPostRow> findScrapPage(long memberId, Long cursorId, int size);

	/** 작성 간격 검사용 마지막 작성 시각. status 무관 — 지웠다 다시 올리는 우회도 잡는다. */
	Optional<LocalDateTime> findLastCreatedAt(long memberId);

	void increaseViewCount(long postId);

	/** like_count 원자 증감 후 갱신된 값을 돌려준다. */
	int applyLikeDelta(long postId, int delta);

	void applyCommentDelta(long postId, int delta);
}
