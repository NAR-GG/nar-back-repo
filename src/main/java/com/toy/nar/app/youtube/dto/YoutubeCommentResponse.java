package com.toy.nar.app.youtube.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeCommentResponse(
	List<CommentThreadItem> items
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CommentThreadItem(
		CommentThreadSnippet snippet
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CommentThreadSnippet(
		CommentWrapper topLevelComment
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CommentWrapper(
		String id,
		CommentSnippet snippet
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CommentSnippet(
		String authorDisplayName,
		String authorProfileImageUrl,
		String textDisplay,
		long likeCount,
		String publishedAt
	) {}
}
