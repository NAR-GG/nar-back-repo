package com.toy.nar.app.youtube.dto;

import java.util.List;
import java.util.Map;

public record YoutubeSearchResponse(
	String nextPageToken,
	String prevPageToken,
	PageInfo pageInfo,
	List<SearchItem> items
) {
	public record PageInfo(Integer totalResults, Integer resultsPerPage) {}

	public record SearchItem(SearchId id, SearchSnippet snippet) {}

	public record SearchId(String kind, String videoId) {}

	public record SearchSnippet(
		String publishedAt,
		String channelId,
		String title,
		String description,
		Map<String, Thumbnail> thumbnails
	) {}

	public record Thumbnail(
		String url,
		Integer width,
		Integer height
	) {}
}

