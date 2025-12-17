package com.toy.nar.app.youtube.dto;

import java.util.List;
import java.util.Map;

public record YoutubePlaylistResponse(
	String nextPageToken,
	String prevPageToken,
	PageInfo pageInfo,
	List<PlaylistItem> items
) {
	public record PageInfo(Integer totalResults, Integer resultsPerPage) {}

	public record PlaylistItem(
		String id,
		Snippet snippet,
		ContentDetails contentDetails
	) {}

	public record Snippet(
		String publishedAt,
		String channelId,
		String title,
		String description,
		Map<String, Thumbnail> thumbnails,
		ResourceId resourceId
	) {}

	public record ResourceId(
		String kind,
		String videoId
	) {}

	public record ContentDetails(
		String videoId,
		String videoPublishedAt
	) {}

	public record Thumbnail(
		String url,
		Integer width,
		Integer height
	) {}
}
