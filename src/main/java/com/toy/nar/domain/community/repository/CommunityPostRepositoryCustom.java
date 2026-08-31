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
	List<CommunityPostRow> findPage(Long boardTeamId, Long cursorId, List<Long> excludedMemberIds, int size,
			boolean includeTest);

	Optional<CommunityPostRow> findRowById(long postId);

	/** 내 스크랩 페이지. 커서는 scrap.id (row 의 scrapId 로 내려간다). */
	List<CommunityPostRow> findScrapPage(long memberId, Long cursorId, int size, boolean includeTest);

	/** 내가 쓴 글. 커서는 post.id. 삭제·블라인드는 숨긴다(내 활동 화면 정책). */
	List<CommunityPostRow> findMyPostPage(long memberId, Long cursorId, int size, boolean includeTest);

	/** 내가 좋아요한 글. 커서는 like.id (row 의 scrapId 슬롯에 실려 내려간다). */
	List<CommunityPostRow> findLikedPage(long memberId, Long cursorId, int size, boolean includeTest);

	/**
	 * 작성 간격 검사용 마지막 작성 시각. status 무관 — 지웠다 다시 올리는 우회도 잡는다.
	 * [boardTeamId] 로 범위를 좁힌다(null 은 전체 게시판) — 간격은 게시판마다 따로 돈다.
	 */
	Optional<LocalDateTime> findLastCreatedAt(long memberId, Long boardTeamId);

	void increaseViewCount(long postId);

	/** like_count 원자 증감 후 갱신된 값을 돌려준다. */
	int applyLikeDelta(long postId, int delta);

	void applyCommentDelta(long postId, int delta);

	/** 첨부 사진 전체 교체(삭제 후 재삽입). 작성 시에는 그냥 삽입이 된다. */
	void replaceImages(long postId, List<String> imageUrls);

	/** 상세용 — VISIBLE 사진만 sort_order 순. id 는 IMAGE 신고 target 으로 쓰인다. */
	List<CommunityPostImageRow> findVisibleImages(long postId);

	/** 목록용 — 각 글의 VISIBLE 사진 전부(post_id, sort_order 순). 썸네일·개수는 호출부가 접는다. */
	List<CommunityPostImageRow> findVisibleImagesByPostIds(List<Long> postIds);
}
