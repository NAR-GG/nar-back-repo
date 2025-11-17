package com.toy.nar.app.youtube.dto;

import java.util.List;

public record YoutubeSearchResponse(
	List<SearchItem> items
) {
	public record SearchItem(SearchId id, SearchSnippet snippet) {}
	public record SearchId(String kind, String videoId) {}
	public record SearchSnippet(
		String publishedAt,
		String channelId,
		String title,
		String description
	) {}
}

