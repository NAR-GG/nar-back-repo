package com.toy.nar.app.youtube;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

	private static final String PART_SNIPPET = "snippet";
	private static final String PART_SNIPPET_STATISTICS = "snippet,statistics";
	private static final String TYPE_VIDEO = "video";
	private static final String ORDER_DATE = "date";
	private static final String VIDEO_DURATION_SHORT = "short";
	private static final int VIDEOS_LIST_MAX_IDS = 50;

	private final WebClient youtubeWebClient;
	private final YoutubeApiProperties properties;

	/**
	 * 여러 채널의 쇼츠(4분 미만) 영상을 합쳐서
	 * 전체 최신순으로 limit 개 가져오기
	 */
	public List<YoutubeVideoDto> getChannelsShortsOrderByLatest(
		List<String> channelIds,
		long limit
	) {
		if (channelIds == null || channelIds.isEmpty()) {
			return List.of();
		}

		long perChannelMax = calculatePerChannelMax(limit);

		List<YoutubeSearchResponse.SearchItem> allItems = channelIds.stream()
			.map(channelId -> searchChannelShorts(channelId, perChannelMax))
			.filter(response -> response != null && response.items() != null)
			.flatMap(response -> response.items().stream())
			.toList();

		if (allItems.isEmpty()) {
			return List.of();
		}

		List<String> videoIds = allItems.stream()
			.map(item -> item.id().videoId())
			.distinct()
			.toList();

		Map<String, YoutubeVideosResponse.VideoItem> videoMap =
			fetchVideoStatisticsInBatches(videoIds);

		return allItems.stream()
			.map(item -> toYoutubeVideoDto(item, videoMap.get(item.id().videoId())))
			.sorted(Comparator.comparing(YoutubeVideoDto::publishedAt).reversed())
			.limit(limit)
			.toList();
	}

	/**
	 * 단일 채널의 쇼츠(4분 미만) 영상을 댓글 수 기준으로 정렬해서 가져오기
	 */
	public List<YoutubeVideoDto> getChannelShortsOrderByComments(
		String channelId,
		long maxResults
	) {
		List<YoutubeVideoDto> latest =
			getChannelsShortsOrderByLatest(List.of(channelId), maxResults);

		return latest.stream()
			.sorted(Comparator.comparingLong(YoutubeVideoDto::commentCount).reversed())
			.toList();
	}

	/**
	 * 단일 채널에서 쇼츠 검색 (search.list)
	 */
	private YoutubeSearchResponse searchChannelShorts(String channelId, long maxResults) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/search")
				.queryParam("part", PART_SNIPPET)
				.queryParam("channelId", channelId)
				.queryParam("type", TYPE_VIDEO)
				.queryParam("order", ORDER_DATE)
				.queryParam("videoDuration", VIDEO_DURATION_SHORT)
				.queryParam("maxResults", maxResults)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeSearchResponse.class)
			.block();
	}

	/**
	 * videos.list 호출 (단일 배치)
	 */
	private Map<String, YoutubeVideosResponse.VideoItem> fetchVideoStatistics(List<String> videoIds) {
		if (videoIds == null || videoIds.isEmpty()) {
			return Map.of();
		}

		String idParam = String.join(",", videoIds);

		YoutubeVideosResponse videosResponse = youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/videos")
				.queryParam("part", PART_SNIPPET_STATISTICS)
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

	/**
	 * 여러 채널에서 모은 videoId 가 50개를 넘을 수 있어서
	 * 50개씩 나눠서 videos.list 를 여러 번 호출
	 */
	private Map<String, YoutubeVideosResponse.VideoItem> fetchVideoStatisticsInBatches(List<String> videoIds) {
		if (videoIds == null || videoIds.isEmpty()) {
			return Map.of();
		}

		Map<String, YoutubeVideosResponse.VideoItem> result = new HashMap<>();

		for (int start = 0; start < videoIds.size(); start += VIDEOS_LIST_MAX_IDS) {
			int end = Math.min(start + VIDEOS_LIST_MAX_IDS, videoIds.size());
			List<String> batch = videoIds.subList(start, end);
			result.putAll(fetchVideoStatistics(batch));
		}

		return result;
	}

	/**
	 * SearchItem + VideoItem -> YoutubeVideoDto 매핑
	 */
	private YoutubeVideoDto toYoutubeVideoDto(
		YoutubeSearchResponse.SearchItem searchItem,
		YoutubeVideosResponse.VideoItem videoItem
	) {
		long viewCount = 0L;
		long likeCount = 0L;
		long commentCount = 0L;

		if (videoItem != null && videoItem.statistics() != null) {
			var stats = videoItem.statistics();
			viewCount = parseLongSafe(stats.viewCount());
			likeCount = parseLongSafe(stats.likeCount());
			commentCount = parseLongSafe(stats.commentCount());
		}

		String thumbnailUrl = extractBestThumbnailUrl(searchItem.snippet().thumbnails());

		return new YoutubeVideoDto(
			searchItem.id().videoId(),
			searchItem.snippet().title(),
			searchItem.snippet().description(),
			OffsetDateTime.parse(searchItem.snippet().publishedAt()),
			viewCount,
			likeCount,
			commentCount,
			thumbnailUrl
		);
	}

	private long parseLongSafe(String value) {
		try {
			return (value == null) ? 0L : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private String extractBestThumbnailUrl(Map<String, YoutubeSearchResponse.Thumbnail> thumbnails) {
		if (thumbnails == null || thumbnails.isEmpty()) {
			return null;
		}

		String[] order = {"high", "medium", "default"};
		for (String key : order) {
			var thumb = thumbnails.get(key);
			if (thumb != null && thumb.url() != null && !thumb.url().isBlank()) {
				return thumb.url();
			}
		}

		return thumbnails.values().stream()
			.filter(t -> t.url() != null && !t.url().isBlank())
			.findFirst()
			.map(YoutubeSearchResponse.Thumbnail::url)
			.orElse(null);
	}

	private long calculatePerChannelMax(long limit) {
		long base = Math.max(limit, 10);
		return Math.min(base, VIDEOS_LIST_MAX_IDS);
	}
}
