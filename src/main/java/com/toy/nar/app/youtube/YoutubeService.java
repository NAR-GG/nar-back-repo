package com.toy.nar.app.youtube;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.app.youtube.dto.YoutubeChannelResponse;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.common.util.YoutubeApiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeService {

	private static final String PART_SNIPPET = "snippet";
	private static final String PART_CHANNEL_INFO = "snippet,contentDetails";
	private static final String TYPE_VIDEO = "video";
	private static final String ORDER_DATE = "date";

	private final WebClient youtubeWebClient;
	private final YoutubeApiProperties properties;

	/**
	 * [SyncService 사용]
	 * 채널의 최신 영상 검색
	 * @param videoDuration "short"이면 쇼츠, null이면 모든 길이의 영상(일반 영상 포함)
	 */
	public YoutubeSearchResponse searchLatestVideos(String channelId, long maxResults, String videoDuration) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> {
				var builder = uriBuilder
					.path("/search")
					.queryParam("part", PART_SNIPPET)
					.queryParam("channelId", channelId)
					.queryParam("type", TYPE_VIDEO)
					.queryParam("order", ORDER_DATE)
					.queryParam("maxResults", maxResults)
					.queryParam("key", properties.getKey());

				if (videoDuration != null) {
					builder.queryParam("videoDuration", videoDuration);
				}

				return builder.build();
			})
			.retrieve()
			.bodyToMono(YoutubeSearchResponse.class)
			.block();
	}

	public List<YoutubeChannelResponse.ChannelItem> getChannelInfos(List<String> channelIds) {
		if (channelIds == null || channelIds.isEmpty()) {
			return List.of();
		}

		String idParam = String.join(",", channelIds);

		YoutubeChannelResponse response = youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/channels")
				.queryParam("part", PART_CHANNEL_INFO)
				.queryParam("id", idParam)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeChannelResponse.class)
			.block();

		if (response == null || response.items() == null) {
			return List.of();
		}

		return response.items();
	}

	public YoutubeChannelResponse getChannelInfo(String channelId) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/channels")
				.queryParam("part", PART_CHANNEL_INFO)
				.queryParam("id", channelId)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeChannelResponse.class)
			.block();
	}

	public String extractBestThumbnailUrl(Map<String, YoutubeSearchResponse.Thumbnail> thumbnails) {
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

	public void subscribeToChannel(String channelId, String callbackUrl) {
		WebClient client = WebClient.create(properties.getPubSubHubbubUrl());

		client.post()
			.uri("/subscribe")
			.header("Content-Type", "application/x-www-form-urlencoded")
			.bodyValue("hub.callback=" + callbackUrl +
				"&hub.mode=subscribe" +
				"&hub.topic=https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
			.retrieve()
			.toBodilessEntity()
			.subscribe(
				response -> log.info("Subscribed to channel: {}", channelId),
				error -> log.error("Failed to subscribe to channel: {}", channelId, error)
			);
	}

	public YoutubeVideoResponse searchVideoById(String videoId) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/videos")
				.queryParam("part", PART_SNIPPET)
				.queryParam("id", videoId)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeVideoResponse.class)
			.block();
	}
}