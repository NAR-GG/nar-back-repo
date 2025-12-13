package com.toy.nar.app.youtube;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.YoutubeCommentResponse;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.common.util.YoutubeProperties;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Comment;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.CommentRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncService {

	private final YoutubeService youtubeService;
	private final ChannelRepository channelRepository;
	private final VideoRepository videoRepository;
	private final CommentRepository commentRepository;
	private final YoutubeProperties youtubeProperties;

	private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
	private static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
	private static final String YOUTUBE_SHORTS_URL = "https://www.youtube.com/shorts/";

	/**
	 * [관리자용 1단계] 채널 목록 초기화 (YML -> DB)
	 * application.yml에 정의된 채널 중 DB에 없는 채널을 찾아 API로 정보를 가져와 저장합니다.
	 */
	@Transactional
	public int initChannelsFromProperties() {
		log.info("### [Admin] 채널 데이터 초기화(From YML) 시작 ###");

		Map<String, ChannelType> idTypeMap = getChannelIdTypeMap();
		if (idTypeMap.isEmpty()) {
			log.warn("yml에 설정된 채널이 없습니다.");
			return 0;
		}

		List<String> newIds = filterNewChannels(idTypeMap.keySet());
		if (newIds.isEmpty()) {
			log.info("### 추가할 신규 채널이 없습니다. (모두 최신 상태) ###");
			return 0;
		}

		log.info("### 신규 채널 {}개 발견. 유튜브 API 조회 중... ###", newIds.size());
		return fetchAndSaveChannels(newIds, idTypeMap);
	}

	private Map<String, ChannelType> getChannelIdTypeMap() {
		return youtubeProperties.seedChannels().entrySet().stream()
			.flatMap(entry -> entry.getValue().stream()
				.map(id -> Map.entry(id, entry.getKey())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private List<String> filterNewChannels(Set<String> allTargetIds) {
		Set<String> existingYoutubeIds = channelRepository.findByYoutubeChannelIdIn(new ArrayList<>(allTargetIds)).stream()
			.map(Channel::getYoutubeChannelId)
			.collect(Collectors.toSet());

		return allTargetIds.stream()
			.filter(id -> !existingYoutubeIds.contains(id))
			.toList();
	}

	private int fetchAndSaveChannels(List<String> newIds, Map<String, ChannelType> idTypeMap) {
		var channelDtos = youtubeService.getChannelInfos(newIds);

		List<Channel> newChannels = channelDtos.stream()
			.map(dto -> {
				String profileUrl = youtubeService.extractBestThumbnailUrl(dto.snippet().thumbnails());
				return Channel.builder()
					.youtubeChannelId(dto.id())
					.channelName(dto.snippet().title())
					.profileImageUrl(profileUrl)
					.uploadPlaylistId(dto.contentDetails().relatedPlaylists().uploads())
					.channelType(idTypeMap.get(dto.id()))
					.build();
			})
			.toList();

		if (!newChannels.isEmpty()) {
			channelRepository.saveAll(newChannels);
			log.info("### 신규 채널 {}개 DB 저장 완료 ###", newChannels.size());
		}

		return newChannels.size();
	}

	/**
	 * [관리자용 2단계] 최근 1주일 영상 데이터 적재
	 * DB에 있는 채널들의 영상을 조회하여 저장합니다.
	 */
	@Transactional
	public void syncLastWeekVideos() {
		List<Channel> channels = channelRepository.findAll();

		if (channels.isEmpty()) {
			log.warn("### 동기화할 채널이 DB에 없습니다. 채널 초기화를 먼저 진행하세요. ###");
			return;
		}

		LocalDateTime oneWeekAgo = LocalDateTime.now(ZONE_KST).minusDays(7);
		log.info("### 비디오 Sync 시작. 기준 시간(KST): {} (대상 채널: {}개) ###", oneWeekAgo, channels.size());

		int totalSaved = 0;
		for (Channel channel : channels) {
			try {
				totalSaved += syncSingleChannelVideos(channel, oneWeekAgo);
			} catch (Exception e) {
				log.error("채널 비디오 동기화 실패: {} ({})", channel.getChannelName(), channel.getYoutubeChannelId(), e);
			}
		}

		log.info("### 비디오 전체 동기화 완료. 총 {}개의 신규 영상 저장됨 ###", totalSaved);
	}

	private int syncSingleChannelVideos(Channel channel, LocalDateTime defaultSince) {
		LocalDateTime lastSavedAt = videoRepository.findLatestPublishedAtByChannel(channel);
		LocalDateTime searchAfter = (lastSavedAt != null) ? lastSavedAt : defaultSince;

		String videoDurationParam = (channel.getChannelType() == ChannelType.SHORTS) ? "short" : null;

		YoutubeSearchResponse searchResponse = youtubeService.searchLatestVideos(
			channel.getYoutubeChannelId(),
			50,
			videoDurationParam
		);

		if (searchResponse == null || searchResponse.items() == null) {
			return 0;
		}

		// 1. 저장 대상 비디오 ID 필터링
		List<String> videoIdsToSave = searchResponse.items().stream()
			.filter(item -> {
				OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
				LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();
				return publishedAtKst.isAfter(searchAfter);
			})
			.filter(item -> !videoRepository.existsByYoutubeVideoId(item.id().videoId()))
			.map(item -> item.id().videoId())
			.toList();

		if (videoIdsToSave.isEmpty()) {
			return 0;
		}

		// 2. 비디오 상세 정보(통계 포함) 조회
		YoutubeVideoResponse videoDetailsResponse = youtubeService.getVideoDetails(videoIdsToSave);

		if (videoDetailsResponse == null || videoDetailsResponse.items() == null) {
			return 0;
		}

		// 3. 엔티티 변환 및 저장
		List<Video> videosToSave = videoDetailsResponse.items().stream()
			.map(item -> buildVideoEntity(
				item.id(),
				item.snippet().title(),
				item.snippet().thumbnails(),
				item.snippet().publishedAt(),
				item.statistics(),
				channel
			))
			.toList();

		if (!videosToSave.isEmpty()) {
			videoRepository.saveAll(videosToSave);
			log.info("[{}] 신규 영상 {}개 저장 (기준: {} 이후)",
				channel.getChannelName(), videosToSave.size(), searchAfter);
			return videosToSave.size();
		}

		return 0;
	}

	@Transactional
	public void processNewVideoNotification(String videoId, String channelId) {
		if (videoRepository.existsByYoutubeVideoId(videoId)) {
			log.info("이미 존재하는 영상입니다. ID: {}", videoId);
			return;
		}

		Channel channel = channelRepository.findByYoutubeChannelId(channelId)
			.orElseThrow(() -> new IllegalArgumentException("관리되지 않는 채널입니다: " + channelId));

		YoutubeVideoResponse response = youtubeService.searchVideoById(videoId);

		if (response != null && response.items() != null && !response.items().isEmpty()) {
			YoutubeVideoResponse.VideoItem item = response.items().get(0);

			Video video = buildVideoEntity(
				item.id(),
				item.snippet().title(),
				item.snippet().thumbnails(),
				item.snippet().publishedAt(),
				item.statistics(),
				channel
			);
			videoRepository.save(video);

			log.info("[PubSub] 실시간 신규 영상 저장 완료: {} - {}", channel.getChannelName(), video.getTitle());
		}
	}

	@Transactional
	public void syncRecentComments() {
		LocalDateTime oneDayAgo = LocalDateTime.now(ZONE_KST).minusDays(1);
		List<Video> recentVideos = videoRepository.findByPublishedAtAfter(oneDayAgo);

		log.info("### 최근 24시간 영상 댓글 동기화 시작. 대상: {}개 ###", recentVideos.size());

		for (Video video : recentVideos) {
			try {
				syncCommentsForVideo(video);
			} catch (Exception e) {
				log.error("댓글 동기화 실패: {} (ID: {})", video.getTitle(), video.getYoutubeVideoId(), e);
			}
		}
	}

	private void syncCommentsForVideo(Video video) {
		YoutubeCommentResponse response = youtubeService.getVideoComments(video.getYoutubeVideoId());

		if (response == null || response.items() == null) {
			return;
		}

		List<Comment> commentsToSave = response.items().stream()
			.map(item -> item.snippet().topLevelComment())
			.filter(comment -> !commentRepository.existsByYoutubeCommentId(comment.id()))
			.map(comment -> {
				OffsetDateTime odt = OffsetDateTime.parse(comment.snippet().publishedAt());
				LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

				return Comment.builder()
					.video(video)
					.youtubeCommentId(comment.id())
					.authorDisplayName(comment.snippet().authorDisplayName())
					.authorProfileImageUrl(comment.snippet().authorProfileImageUrl())
					.textDisplay(comment.snippet().textDisplay())
					.likeCount(comment.snippet().likeCount())
					.publishedAt(publishedAtKst)
					.build();
			})
			.toList();

		if (!commentsToSave.isEmpty()) {
			commentRepository.saveAll(commentsToSave);
			log.info("[댓글 Sync] {} - 신규 댓글 {}개 저장", video.getTitle(), commentsToSave.size());
		}
	}

	public void subscribeAllChannels(String callbackBaseUrl) {
		List<Channel> channels = channelRepository.findAll();
		String callbackUrl = callbackBaseUrl + "/api/youtube/webhook";

		for (Channel channel : channels) {
			try {
				youtubeService.subscribeToChannel(channel.getYoutubeChannelId(), callbackUrl);
			} catch (Exception e) {
				log.error("구독 요청 실패: {}", channel.getChannelName());
			}
		}
	}

	private Video buildVideoEntity(String videoId, String title, Map<String, YoutubeSearchResponse.Thumbnail> thumbnails,
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