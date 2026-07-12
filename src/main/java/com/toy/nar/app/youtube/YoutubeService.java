package com.toy.nar.app.youtube;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.app.youtube.dto.YoutubeChannelResponse;
import com.toy.nar.app.youtube.dto.YoutubePlaylistResponse;
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
	private static final String PART_VIDEO_DETAILS = "snippet,statistics";
	private static final String PART_COMMENT_SNIPPET = "snippet";
	private static final String TYPE_VIDEO = "video";
	private static final String ORDER_DATE = "date";

	private final WebClient youtubeWebClient;
	private final YoutubeApiProperties properties;

	/**
	 * [SyncService 사용] PlaylistItems API를 통해 채널의 영상 목록 조회 (비용 1 unit)
	 */
	public YoutubePlaylistResponse getPlaylistItems(String playlistId, String pageToken) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> {
				var builder = uriBuilder
					.path("/playlistItems")
					.queryParam("part", PART_SNIPPET)
					.queryParam("playlistId", playlistId)
					.queryParam("maxResults", 50)
					.queryParam("key", properties.getKey());

				if (pageToken != null && !pageToken.isBlank()) {
					builder.queryParam("pageToken", pageToken);
				}

				return builder.build();
			})
			.retrieve()
			.bodyToMono(YoutubePlaylistResponse.class)
			.block();
	}

	/**
	 * [SyncService 사용]
	 * 채널의 최신 영상 검색
	 * @param videoDuration "short"이면 쇼츠, null이면 모든 길이의 영상(일반 영상 포함)
	 */
	public YoutubeSearchResponse searchLatestVideos(String channelId, long maxResults, String videoDuration, String pageToken) {
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
				
				if (pageToken != null && !pageToken.isBlank()) {
					builder.queryParam("pageToken", pageToken);
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

	/**
	 * PubSub 구독 요청. 성공 여부를 반환한다 — 호출부가 실패를 집계해 알림으로 잇는다.
	 * 순차 블로킹 + 타임아웃 + 재시도: 기동 직후 CPU 포화로 이벤트루프 write 가 밀리면
	 * Google 이 유휴 커넥션을 끊어(Broken pipe) 일괄 실패하던 문제를 견디게 한다.
	 * 실패는 스택트레이스 없이 한 줄만 남긴다(reactor 스택 ~70줄 도배 방지).
	 */
	public boolean subscribeToChannel(String channelId, String callbackUrl) {
		try {
			WebClient.create(properties.getPubSubHubbubUrl()).post()
				.uri("/subscribe")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.bodyValue("hub.callback=" + callbackUrl +
					"&hub.mode=subscribe" +
					"&hub.topic=https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
				.retrieve()
				.toBodilessEntity()
				.timeout(java.time.Duration.ofSeconds(10))
				.retryWhen(reactor.util.retry.Retry.backoff(2, java.time.Duration.ofSeconds(2)))
				.block();
			return true;
		} catch (Exception e) {
			log.warn("Failed to subscribe to channel {}: {}", channelId, e.toString());
			return false;
		}
	}

	public YoutubeVideoResponse searchVideoById(String videoId) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/videos")
				.queryParam("part", PART_VIDEO_DETAILS)
				.queryParam("id", videoId)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeVideoResponse.class)
			.block();
	}

	public YoutubeVideoResponse getVideoDetails(List<String> videoIds) {
		if (videoIds == null || videoIds.isEmpty()) {
			return null;
		}

		String idParam = String.join(",", videoIds);

		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/videos")
				.queryParam("part", PART_VIDEO_DETAILS)
				.queryParam("id", idParam)
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(YoutubeVideoResponse.class)
			.block();
	}

	public com.toy.nar.app.youtube.dto.YoutubeCommentResponse getVideoComments(String videoId) {
		return youtubeWebClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/commentThreads")
				.queryParam("part", PART_COMMENT_SNIPPET)
				.queryParam("videoId", videoId)
				.queryParam("maxResults", 50)
				.queryParam("order", "time")
				.queryParam("key", properties.getKey())
				.build()
			)
			.retrieve()
			.bodyToMono(com.toy.nar.app.youtube.dto.YoutubeCommentResponse.class)
			.block();
	}
}