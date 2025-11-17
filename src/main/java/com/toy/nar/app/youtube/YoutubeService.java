package com.toy.nar.app.youtube;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideosResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoDto;
import com.toy.nar.common.util.YoutubeApiProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YoutubeService {

	private final WebClient youtubeWebClient;
	private final YoutubeApiProperties properties;

	/**
	 * 채널의 쇼츠(4분 미만) 영상을 최신순으로 가져오기
	 */
	public List<YoutubeVideoDto> getChannelShortsOrderByLatest(
		String channelId,
		long maxResults
	) {
		YoutubeSearchResponse searchResponse = youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/search")
				.queryParam("part", "snippet")
				.queryParam("channelId", channelId)
				.queryParam("type", "video")
				.queryParam("order", "date")
				.queryParam("videoDuration", "short")
				.queryParam("maxResults", maxResults)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeSearchResponse.class)
			.block();

		if (searchResponse == null || searchResponse.items() == null || searchResponse.items().isEmpty()) {
			return List.of();
		}

		List<String> videoIds = searchResponse.items().stream()
			.map(item -> item.id().videoId())
			.toList();

		Map<String, YoutubeVideosResponse.VideoItem> videoMap =
			fetchVideoStatistics(videoIds);

		return searchResponse.items().stream()
			.map(item -> {
				YoutubeVideosResponse.VideoItem videoItem =
					videoMap.get(item.id().videoId());

				long viewCount = 0L;
				long likeCount = 0L;
				long commentCount = 0L;

				if (videoItem != null && videoItem.statistics() != null) {
					var stats = videoItem.statistics();
					viewCount = parseLongSafe(stats.viewCount());
					likeCount = parseLongSafe(stats.likeCount());
					commentCount = parseLongSafe(stats.commentCount());
				}

				return new YoutubeVideoDto(
					item.id().videoId(),
					item.snippet().title(),
					item.snippet().description(),
					OffsetDateTime.parse(item.snippet().publishedAt()),
					viewCount,
					likeCount,
					commentCount
				);
			})
			.sorted(Comparator.comparing(YoutubeVideoDto::publishedAt).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * 채널의 쇼츠(4분 미만) 영상을 댓글 수 기준으로 정렬해서 가져오기
	 */
	public List<YoutubeVideoDto> getChannelShortsOrderByComments(
		String channelId,
		long maxResults
	) {
		// 1단계: 최신순으로 N개까지 일단 받아온다 (search.list)
		List<YoutubeVideoDto> latest = getChannelShortsOrderByLatest(channelId, maxResults);

		// 2단계: 이미 통계까지 채워져 있으니 commentCount 기준으로 다시 정렬
		return latest.stream()
			.sorted(Comparator.comparingLong(YoutubeVideoDto::commentCount).reversed())
			.collect(Collectors.toList());
	}

	private Map<String, YoutubeVideosResponse.VideoItem> fetchVideoStatistics(List<String> videoIds) {
		if (videoIds.isEmpty()) {
			return Map.of();
		}

		String idParam = String.join(",", videoIds);

		YoutubeVideosResponse videosResponse = youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/videos")
				.queryParam("part", "snippet,statistics")
				.queryParam("id", idParam)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeVideosResponse.class)
			.block();

		if (videosResponse == null || videosResponse.items() == null) {
			return Map.of();
		}

		return videosResponse.items().stream()
			.collect(Collectors.toMap(YoutubeVideosResponse.VideoItem::id, v -> v));
	}

	private long parseLongSafe(String value) {
		try {
			return value == null ? 0L : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
