package com.toy.nar.app.youtube.dto;

import java.util.List;
import java.util.Map;

public record YoutubeChannelResponse(
	List<ChannelItem> items
) {
	public record ChannelItem(
		String id,
		Snippet snippet,
		ContentDetails contentDetails
	) {}

	public record Snippet(
		String title,
		String description,
		Map<String, YoutubeSearchResponse.Thumbnail> thumbnails
	) {}

	public record ContentDetails(
		RelatedPlaylists relatedPlaylists
	) {}

	public record RelatedPlaylists(
		String uploads
	) {}
}