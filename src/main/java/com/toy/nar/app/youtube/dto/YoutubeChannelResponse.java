package com.toy.nar.app.youtube.dto;

import java.util.List;

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
		String description
	) {}

	public record ContentDetails(
		RelatedPlaylists relatedPlaylists
	) {}

	public record RelatedPlaylists(
		String uploads
	) {}
}