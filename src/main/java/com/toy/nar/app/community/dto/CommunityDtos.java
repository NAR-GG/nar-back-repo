package com.toy.nar.app.community.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 커뮤니티 요청·응답 DTO 모음. 전부 작은 레코드라 파일 하나에 모은다.
 *
 * <p>author 가 null 이면 앱이 "탈퇴한 사용자"로 그린다(회원 하드 삭제 SET NULL).
 * 차단·삭제된 댓글은 자리(status)만 남고 body 가 null 이다.</p>
 */
public final class CommunityDtos {

	private CommunityDtos() {
	}

	/* ---------- 요청 ---------- */

	/**
	 * imageUrls: 서명 업로드 후 받은 secure_url 목록(≤5). 우리 Cloudinary URL 만 통과한다.
	 * bodyFormat: null/PLAIN = 평문, BLOCKS = body 가 블록 JSON 배열(서버가 파싱·검증).
	 * BLOCKS 면 imageUrls 는 무시된다 — 이미지는 블록에서 추출해 동기화한다.
	 */
	public record PostCreateRequest(Long boardTeamId, String title, String body, String bodyFormat,
			List<String> imageUrls, PollCreateRequest poll) {
	}

	/** 글에 붙는 투표(글당 1개, 작성 시에만). 질문 ≤100자, 선택지 2~4개(각 ≤50자). */
	public record PollCreateRequest(String question, List<String> options) {
	}

	/** voteCount 는 결과 비공개 상태(미투표 + 숨김 설정)면 null 로 온다. */
	public record PollOptionResponse(Long id, String label, Integer voteCount) {
	}

	/**
	 * resultsVisible: 분포(선택지별 표 수)를 보여줄 수 있는가 — 투표했거나 공개 설정.
	 * myOptionId: 내가 고른 선택지(비로그인·미투표면 null). 투표 후 변경 불가.
	 */
	public record PollResponse(Long id, String question, int totalVotes, boolean resultsVisible,
			Long myOptionId, List<PollOptionResponse> options) {
	}

	public record PollVoteRequest(Long optionId) {
	}

	/** imageUrls 는 전체 교체다 — null 은 변경 없음, 빈 배열은 전부 제거. bodyFormat 은 작성과 동일. */
	public record PostUpdateRequest(String title, String body, String bodyFormat, List<String> imageUrls) {
	}

	/** replyToCommentId 만 받는다 — parent 올려붙이기·멘션 대상은 서버가 유도한다(위조 방지). */
	public record CommentCreateRequest(String body, Long replyToCommentId) {
	}

	/** 댓글 수정 — 본문만 바꾼다. 멘션·답글 관계는 수정으로 안 바뀐다. */
	public record CommentUpdateRequest(String body) {
	}

	/** targetType: POST/COMMENT/IMAGE, reason: ABUSE/OBSCENE/AD/FRAUD/SPAM/ETC (ETC 는 detail 필수). */
	public record ReportCreateRequest(String targetType, Long targetId, String reason, String detail) {
	}

	public record BlockCreateRequest(Long memberId) {
	}

	/* ---------- 응답 ---------- */

	public record AuthorResponse(Long memberId, String nickname, String profileImageUrl,
			Long teamId, String teamCode, String teamImageUrl) {
	}

	/**
	 * boardTeamId 가 null 이면 전체 게시판, 값이 있으면 그 팀 게시판이다.
	 *
	 * <p>boardTeamCode 를 같이 싣는 이유: 내 활동 목록(내 글·좋아요·스크랩)은 여러 게시판 글이
	 * 섞여 나오므로 줄마다 어느 게시판인지 배지를 붙여야 한다. id 만 주면 앱이 팀 목록 API
	 * (`/auth/onboarding/teams`)를 받아 매핑해야 하는데, 그 호출이 실패하면 배지가 통째로 사라진다.
	 * 이 쿼리는 이미 author_team_id 로 teams 를 조인하고 있어 컬럼 하나 더 얹는 비용뿐이다.
	 *
	 * <p>전체 게시판이면 boardTeamCode 도 null 이다.
	 */
	public record PostSummaryResponse(Long id, Long boardTeamId, String boardTeamCode,
			String title, String bodyPreview,
			AuthorResponse author, int viewCount, int likeCount, int commentCount,
			boolean edited, LocalDateTime createdAt, String thumbnailUrl, int imageCount,
			boolean hasPoll) {
	}

	/** id 는 IMAGE 신고의 targetId 로 쓴다. */
	public record PostImageResponse(Long id, String url) {
	}

	/**
	 * 게시판 쓰기 자격 판정 — 잠금 바용. 팀 게시판 + 로그인일 때만 내려간다(그 외 null).
	 * reason: NOT_FAN(응원팀 아님) / null(쓰기 자격 있음).
	 * nextWritableAt: 작성 간격(D-9)에 걸려 있으면 다음 작성 가능 시각, 아니면 null.
	 * 자격(reason)과 간격(nextWritableAt)은 별개다 — 자격이 있어도 방금 썼으면 기다린다.
	 */
	public record BoardViewerResponse(boolean canWrite, String reason, LocalDateTime nextWritableAt) {
	}

	public record PostListResponse(List<PostSummaryResponse> posts, Long nextCursor,
			BoardViewerResponse boardViewer) {
	}

	/** blockedAuthor 면 title/body 는 null — 앱이 "차단한 사용자의 글" 자리를 그린다. */
	/** notificationEnabled: 이 글에서 오는 알림 수신 여부(기본 true, 벨 토글로 끔). 비로그인은 true. */
	public record PostViewerResponse(boolean liked, boolean scrapped, boolean mine, boolean blockedAuthor,
			boolean notificationEnabled) {
	}

	public record NotificationToggleResponse(boolean enabled) {
	}

	/**
	 * 상세도 boardTeamCode 를 싣는다. 헤더가 '{팀} 게시판'인데 뒤로가기·더보기 아이콘 사이
	 * 좁은 폭이라 팀 풀네임('한화생명e스포츠')은 들어가지 않는다 — 코드('HLE')가 필요하다.
	 */
	/** bodyFormat 이 BLOCKS 면 body 는 블록 JSON — 앱 렌더러가 이 값으로 해석을 가른다. */
	public record PostDetailResponse(Long id, Long boardTeamId, String boardTeamCode,
			String title, String body, String bodyFormat,
			AuthorResponse author, int viewCount, int likeCount, int commentCount,
			boolean edited, LocalDateTime createdAt, PostViewerResponse viewer,
			List<PostImageResponse> images, PollResponse poll) {
	}

	/** 링크 프리뷰(OG 태그) 스냅샷. 못 긁으면 title 이하 전부 null — 앱은 맨 링크로 그린다. */
	public record LinkPreviewResponse(String url, String title, String description,
			String imageUrl, String siteName) {
	}

	/** status: VISIBLE / DELETED / HIDDEN / BLOCKED. VISIBLE 이 아니면 body 는 null. */
	public record CommentResponse(Long id, Long parentId, String body, String status,
			AuthorResponse author, String mentionNickname, int likeCount,
			boolean liked, boolean mine, boolean edited, LocalDateTime createdAt) {
	}

	public record CommentListResponse(List<CommentResponse> comments, Long nextCursor) {
	}

	public record LikeToggleResponse(boolean liked, int likeCount) {
	}

	public record ScrapToggleResponse(boolean scrapped) {
	}

	public record ScrapItemResponse(Long scrapId, PostSummaryResponse post) {
	}

	public record ScrapListResponse(List<ScrapItemResponse> items, Long nextCursor) {
	}

	/** 내가 좋아요한 글. 커서는 likeId. */
	public record LikedPostItemResponse(Long likeId, PostSummaryResponse post) {
	}

	public record LikedPostListResponse(List<LikedPostItemResponse> items, Long nextCursor) {
	}

	/**
	 * 내가 쓴 댓글. postTitle 로 원글 이동. 삭제·블라인드(댓글·원글 모두)는 목록에서 빠진다.
	 *
	 * <p>boardTeamId/boardTeamCode 는 **원글이 속한 게시판**이다(댓글 작성자의 응원팀이 아니다).
	 * 내 활동 목록은 게시판이 섞여 나오므로 이게 없으면 어느 게시판 댓글인지 알 방법이 없다.
	 */
	public record MyCommentResponse(Long id, Long postId, String postTitle,
			Long boardTeamId, String boardTeamCode, String body,
			int likeCount, boolean edited, LocalDateTime createdAt) {
	}

	public record MyCommentListResponse(List<MyCommentResponse> comments, Long nextCursor) {
	}
}
