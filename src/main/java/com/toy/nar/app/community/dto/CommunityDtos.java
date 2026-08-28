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

	/** imageUrls: 서명 업로드 후 받은 secure_url 목록(≤5). 우리 Cloudinary URL 만 통과한다. */
	public record PostCreateRequest(Long boardTeamId, String title, String body, List<String> imageUrls) {
	}

	/** imageUrls 는 전체 교체다 — null 은 변경 없음, 빈 배열은 전부 제거. */
	public record PostUpdateRequest(String title, String body, List<String> imageUrls) {
	}

	/** replyToCommentId 만 받는다 — parent 올려붙이기·멘션 대상은 서버가 유도한다(위조 방지). */
	public record CommentCreateRequest(String body, Long replyToCommentId) {
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

	public record PostSummaryResponse(Long id, Long boardTeamId, String title, String bodyPreview,
			AuthorResponse author, int viewCount, int likeCount, int commentCount,
			boolean edited, LocalDateTime createdAt, String thumbnailUrl, int imageCount) {
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
	public record PostViewerResponse(boolean liked, boolean scrapped, boolean mine, boolean blockedAuthor) {
	}

	public record PostDetailResponse(Long id, Long boardTeamId, String title, String body,
			AuthorResponse author, int viewCount, int likeCount, int commentCount,
			boolean edited, LocalDateTime createdAt, PostViewerResponse viewer,
			List<PostImageResponse> images) {
	}

	/** status: VISIBLE / DELETED / HIDDEN / BLOCKED. VISIBLE 이 아니면 body 는 null. */
	public record CommentResponse(Long id, Long parentId, String body, String status,
			AuthorResponse author, String mentionNickname, int likeCount,
			boolean liked, boolean mine, LocalDateTime createdAt) {
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

	/** 내가 쓴 댓글. postTitle 로 원글 이동. 삭제·블라인드(댓글·원글 모두)는 목록에서 빠진다. */
	public record MyCommentResponse(Long id, Long postId, String postTitle, String body,
			int likeCount, LocalDateTime createdAt) {
	}

	public record MyCommentListResponse(List<MyCommentResponse> comments, Long nextCursor) {
	}
}
