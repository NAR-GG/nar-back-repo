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

	public record PostCreateRequest(Long boardTeamId, String title, String body) {
	}

	public record PostUpdateRequest(String title, String body) {
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
			boolean edited, LocalDateTime createdAt) {
	}

	public record PostListResponse(List<PostSummaryResponse> posts, Long nextCursor) {
	}

	/** blockedAuthor 면 title/body 는 null — 앱이 "차단한 사용자의 글" 자리를 그린다. */
	public record PostViewerResponse(boolean liked, boolean scrapped, boolean mine, boolean blockedAuthor) {
	}

	public record PostDetailResponse(Long id, Long boardTeamId, String title, String body,
			AuthorResponse author, int viewCount, int likeCount, int commentCount,
			boolean edited, LocalDateTime createdAt, PostViewerResponse viewer) {
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
}
