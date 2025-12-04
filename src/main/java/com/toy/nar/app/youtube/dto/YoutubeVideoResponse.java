package com.toy.nar.app.youtube.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeVideoResponse(
	List<VideoItem> items
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record VideoItem(
		String id,
		YoutubeSearchResponse.SearchSnippet snippet
	) {}
}