package com.toy.nar.app.youtube.dto;

import java.time.LocalDateTime;

import com.toy.nar.domain.youtube.Video;

public record VideoListResponse(
	Long videoId,
	String title,
	String videoUrl,
	String thumbnailUrl,
	LocalDateTime publishedAt,
	// 채널 정보
	Long channelId,
	String channelName,
	String channelProfileUrl
) {
	public static VideoListResponse from(Video video) {
		return new VideoListResponse(
			video.getId(),
			video.getTitle(),
			video.getVideoUrl(),
			video.getThumbnailUrl(),
			video.getPublishedAt(),
			video.getChannel().getId(),
			video.getChannel().getChannelName(),
			video.getChannel().getProfileImageUrl()
		);
	}
}
