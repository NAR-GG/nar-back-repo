package com.toy.nar.app.youtube;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YoutubeChannelSyncTxService {

	private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
	private static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
	private static final String YOUTUBE_SHORTS_URL = "https://www.youtube.com/shorts/";

	private final ChannelRepository channelRepository;
	private final VideoRepository videoRepository;
	private final YoutubeService youtubeService;

	@Transactional
	public int applyChannelVideos(ChannelVideoSyncPayload payload) {
		if (payload.items().isEmpty()) {
			return 0;
		}

		Channel channel = channelRepository.findByYoutubeChannelId(payload.channelYoutubeId())
				.orElseThrow(() -> new IllegalArgumentException("관리되지 않는 채널입니다: " + payload.channelYoutubeId()));

		Map<String, YoutubeVideoResponse.VideoItem> itemByVideoId = payload.items().stream()
				.collect(Collectors.toMap(
						YoutubeVideoResponse.VideoItem::id,
						item -> item,
						(existing, ignored) -> existing,
						LinkedHashMap::new));
		List<String> requestedVideoIds = payload.items().stream()
				.map(YoutubeVideoResponse.VideoItem::id)
				.distinct()
				.toList();

		List<Video> existingVideos = videoRepository.findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(requestedVideoIds);
		Set<String> existingVideoIds = existingVideos.stream()
				.map(Video::getYoutubeVideoId)
				.collect(Collectors.toSet());

		List<Video> videosToSave = new ArrayList<>();

		for (Video video : existingVideos) {
			YoutubeVideoResponse.VideoItem item = itemByVideoId.get(video.getYoutubeVideoId());
			if (item == null) {
				continue;
			}

			String bestThumbnail = youtubeService.extractBestThumbnailUrl(item.snippet().thumbnails());
			video.updateInfo(item.snippet().title(), bestThumbnail);

			if (item.statistics() != null) {
				video.updateStatistics(
						parseCount(item.statistics().viewCount()),
						parseCount(item.statistics().likeCount()),
						parseCount(item.statistics().commentCount()));
			}

			videosToSave.add(video);
		}

		for (YoutubeVideoResponse.VideoItem item : payload.items()) {
			if (existingVideoIds.contains(item.id())) {
				continue;
			}

			Video newVideo = buildVideoEntity(
					item.id(),
					item.snippet().title(),
					item.snippet().thumbnails(),
					item.snippet().publishedAt(),
					item.statistics(),
					channel);
			videosToSave.add(newVideo);
		}

		if (videosToSave.isEmpty()) {
			return 0;
		}

		videoRepository.saveAll(videosToSave);
		return videosToSave.size();
	}

	private Video buildVideoEntity(String videoId, String title,
			Map<String, YoutubeSearchResponse.Thumbnail> thumbnails,
			String publishedAtStr, YoutubeVideoResponse.VideoStatistics statistics, Channel channel) {
		String thumbnailUrl = youtubeService.extractBestThumbnailUrl(thumbnails);

		OffsetDateTime odt = OffsetDateTime.parse(publishedAtStr);
		LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

		String videoUrl = (channel.getChannelType() == ChannelType.SHORTS)
				? YOUTUBE_SHORTS_URL + videoId
				: YOUTUBE_WATCH_URL + videoId;

		Video.VideoBuilder builder = Video.builder()
				.channel(channel)
				.youtubeVideoId(videoId)
				.title(title)
				.thumbnailUrl(thumbnailUrl)
				.videoUrl(videoUrl)
				.publishedAt(publishedAtKst);

		if (statistics != null) {
			builder.viewCount(parseCount(statistics.viewCount()))
					.likeCount(parseCount(statistics.likeCount()))
					.commentCount(parseCount(statistics.commentCount()));
		}

		return builder.build();
	}

	private Long parseCount(String count) {
		if (count == null || count.isBlank()) {
			return 0L;
		}
		try {
			return Long.parseLong(count);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
