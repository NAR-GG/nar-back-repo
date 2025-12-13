package com.toy.nar.app.youtube.dto;

import java.time.LocalDateTime;

import com.toy.nar.domain.youtube.Video;

public record VideoListResponse(
	Long videoId,
	String youtubeVideoId,
	String title,
	String videoUrl,
	String thumbnailUrl,
	String channelType,
	LocalDateTime publishedAt,
	Long viewCount,
	Long likeCount,
	Long commentCount,
	// 채널 정보
	Long channelId,
	String channelName,
	String channelProfileUrl
) {
	public static VideoListResponse from(Video video) {
		return new VideoListResponse(
			video.getId(),
			video.getYoutubeVideoId(),
			video.getTitle(),
			video.getVideoUrl(),
			video.getThumbnailUrl(),
			video.getChannel().getChannelType().name(),
			video.getPublishedAt(),
			video.getViewCount(),
			video.getLikeCount(),
			video.getCommentCount(),
			video.getChannel().getId(),
			video.getChannel().getChannelName(),
			video.getChannel().getProfileImageUrl()
		);
	}
}
