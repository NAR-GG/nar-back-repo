package com.toy.nar.app.youtube.dto;

import java.time.OffsetDateTime;

public record YoutubeVideoDto(
	String videoId,
	String title,
	String description,
	OffsetDateTime publishedAt,
	long viewCount,
	long likeCount,
	long commentCount,
	String thumbnailUrl
) {}
