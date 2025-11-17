package com.toy.nar.app.youtube.dto;

import java.util.List;

public record YoutubeVideosResponse(
	List<VideoItem> items
) {
	public record VideoItem(
		String id,
		VideoSnippet snippet,
		VideoStatistics statistics
	) {}

	public record VideoSnippet(
		String publishedAt,
		String title,
		String description
	) {}

	public record VideoStatistics(
		String viewCount,
		String likeCount,
		String commentCount
	) {}
}
