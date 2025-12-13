package com.toy.nar.app.youtube.dto;

import java.time.LocalDateTime;

import com.toy.nar.domain.youtube.Comment;

public record CommentResponse(
	String youtubeCommentId,
	String authorDisplayName,
	String authorProfileImageUrl,
	String textDisplay,
	Long likeCount,
	LocalDateTime publishedAt
) {
	public static CommentResponse from(Comment comment) {
		return new CommentResponse(
			comment.getYoutubeCommentId(),
			comment.getAuthorDisplayName(),
			comment.getAuthorProfileImageUrl(),
			comment.getTextDisplay(),
			comment.getLikeCount(),
			comment.getPublishedAt()
		);
	}
}
