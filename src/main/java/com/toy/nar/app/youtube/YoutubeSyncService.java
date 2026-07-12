package com.toy.nar.app.youtube;

import static org.springframework.transaction.TransactionDefinition.*;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.toy.nar.app.youtube.dto.YoutubeCommentResponse;
import com.toy.nar.app.youtube.dto.YoutubePlaylistResponse;
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
	private final YoutubeChannelSyncTxService youtubeChannelSyncTxService;
	private final YoutubeProperties youtubeProperties;
	private final PlatformTransactionManager transactionManager;

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
		Set<String> existingYoutubeIds = channelRepository.findByYoutubeChannelIdIn(new ArrayList<>(allTargetIds))
				.stream()
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
	public void syncLastWeekVideos() {
		syncVideosByPeriod(7);
	}

	/**
	 * [관리자용 옵션] 최근 1달 영상 데이터 적재
	 * 초기 세팅 시 사용 권장
	 */
	public void syncLastMonthVideos() {
		syncVideosByPeriod(30);
	}

	private void syncVideosByPeriod(int daysAgo) {
		List<Channel> channels = channelRepository.findAll();

		if (channels.isEmpty()) {
			log.warn("### 동기화할 채널이 DB에 없습니다. 채널 초기화를 먼저 진행하세요. ###");
			return;
		}

		LocalDateTime searchAfter = LocalDateTime.now(ZONE_KST).minusDays(daysAgo);
		log.info("### 비디오 Sync 시작. 기준: 최근 {}일 ({} 이후), 대상 채널: {}개 ###", daysAgo, searchAfter, channels.size());

		int totalSaved = 0;
		for (Channel channel : channels) {
			try {
				ChannelVideoSyncPayload payload = collectChannelVideos(channel, searchAfter);
				totalSaved += youtubeChannelSyncTxService.applyChannelVideos(payload);
			} catch (Exception e) {
				log.error("채널 비디오 동기화 실패: {} ({})", channel.getChannelName(), channel.getYoutubeChannelId(), e);
			}
		}

		log.info("### 비디오 전체 동기화 완료. 총 {}개의 신규 영상 저장됨 ###", totalSaved);
	}

	/**
	 * PlaylistItems API를 사용하여 채널 단위 영상 데이터를 수집합니다. (비용 1 unit)
	 * 외부 API 호출은 트랜잭션 밖에서 수행하고, DB 반영만 별도 채널 트랜잭션으로 처리합니다.
	 */
	private ChannelVideoSyncPayload collectChannelVideos(Channel channel, LocalDateTime searchAfter) {
		String uploadPlaylistId = channel.getUploadPlaylistId();

		// 1. Upload Playlist ID가 없으면 긴급 조회
		if (uploadPlaylistId == null || uploadPlaylistId.isBlank()) {
			try {
				var channelInfo = youtubeService.getChannelInfo(channel.getYoutubeChannelId());
				if (channelInfo != null && channelInfo.items() != null && !channelInfo.items().isEmpty()) {
					uploadPlaylistId = channelInfo.items().get(0).contentDetails().relatedPlaylists().uploads();
					channel.updateUploadPlaylistId(uploadPlaylistId);
					channelRepository.save(channel);
					log.info("[{}] Upload Playlist ID 업데이트 완료: {}", channel.getChannelName(), uploadPlaylistId);
				}
			} catch (Exception e) {
				log.error("채널 정보 조회 실패(Upload Playlist ID 확보 중): {}", channel.getChannelName(), e);
				return ChannelVideoSyncPayload.empty(channel.getYoutubeChannelId());
			}
		}

		if (uploadPlaylistId == null) {
			log.warn("Upload Playlist ID를 찾을 수 없어 동기화 스킵: {}", channel.getChannelName());
			return ChannelVideoSyncPayload.empty(channel.getYoutubeChannelId());
		}

		String nextPageToken = null;
		Map<String, YoutubeVideoResponse.VideoItem> itemByVideoId = new LinkedHashMap<>();

		// 2. PlaylistItems 반복 조회
		do {
			YoutubePlaylistResponse playlistResponse = youtubeService.getPlaylistItems(
					uploadPlaylistId,
					nextPageToken);

			if (playlistResponse == null || playlistResponse.items() == null || playlistResponse.items().isEmpty()) {
				break;
			}

			List<String> videoIdsToProcess = new ArrayList<>();
			boolean stopFetching = false;

			for (var item : playlistResponse.items()) {
				OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
				LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

				if (publishedAtKst.isAfter(searchAfter)) {
					// ResourceId에서 Video ID 추출
					videoIdsToProcess.add(item.snippet().resourceId().videoId());
				} else {
					// 기준 시간 이전 영상이 나오면 중단 (최신순 정렬 가정)
					stopFetching = true;
				}
			}

			if (videoIdsToProcess.isEmpty()) {
				if (stopFetching)
					break;
				nextPageToken = playlistResponse.nextPageToken();
				continue;
			}

			mergeVideoDetails(itemByVideoId, videoIdsToProcess);

			if (stopFetching) {
				break;
			}

			nextPageToken = playlistResponse.nextPageToken();

		} while (nextPageToken != null && !nextPageToken.isBlank());

		if (!itemByVideoId.isEmpty()) {
			log.info("[{}] 영상 {}개 수집 완료 (PlaylistItems API)", channel.getChannelName(), itemByVideoId.size());
		}

		return new ChannelVideoSyncPayload(channel.getYoutubeChannelId(), new ArrayList<>(itemByVideoId.values()));
	}

	private void mergeVideoDetails(Map<String, YoutubeVideoResponse.VideoItem> itemByVideoId, List<String> videoIds) {
		YoutubeVideoResponse videoDetailsResponse = youtubeService.getVideoDetails(videoIds);

		if (videoDetailsResponse == null || videoDetailsResponse.items() == null) {
			return;
		}
		videoDetailsResponse.items().forEach(item -> itemByVideoId.putIfAbsent(item.id(), item));
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
					channel);
			videoRepository.save(video);

			log.info("[PubSub] 실시간 신규 영상 저장 완료: {} - {}", channel.getChannelName(), video.getTitle());
		}
	}

	@Transactional
	public void syncRecentComments() {
		LocalDateTime oneDayAgo = LocalDateTime.now(ZONE_KST).minusDays(1);
		List<Video> recentVideos = videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(oneDayAgo);

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

		// 먼저 모든 댓글 ID를 추출
		List<String> allCommentIds = response.items().stream()
				.map(item -> item.snippet().topLevelComment().id())
				.toList();

		// 이미 존재하는 댓글 ID를 한 번에 조회 (Stream 내부에서 DB 호출 방지)
		Set<String> existingIds = commentRepository.findByYoutubeCommentIdIn(allCommentIds).stream()
				.map(Comment::getYoutubeCommentId)
				.collect(Collectors.toSet());

		List<Comment> commentsToSave = response.items().stream()
				.map(item -> item.snippet().topLevelComment())
				.filter(comment -> !existingIds.contains(comment.id())) // 메모리에서 필터링
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
			int savedCount = 0;
			for (Comment comment : commentsToSave) {
				try {
					// 트랜잭션을 분리하여(REQUIRES_NEW) 하나의 댓글 저장이 실패해도
					// 전체 트랜잭션(영상 통계 업데이트)이 롤백되지 않도록 함
					var transactionTemplate = new TransactionTemplate(
							transactionManager);
					transactionTemplate.setPropagationBehavior(
							PROPAGATION_REQUIRES_NEW);

					Boolean isSaved = transactionTemplate.execute(status -> {
						int result = commentRepository.insertIgnore(
								comment.getVideo().getId(),
								comment.getYoutubeCommentId(),
								comment.getAuthorDisplayName(),
								comment.getAuthorProfileImageUrl(),
								comment.getTextDisplay(),
								comment.getLikeCount(),
								comment.getPublishedAt());

						return result > 0 ? Boolean.TRUE : Boolean.FALSE;
					});

					if (Boolean.TRUE.equals(isSaved)) {
						savedCount++;
					}
				} catch (Exception e) {
					// 중복 댓글(DataIntegrityViolationException) 등 저장 실패 시 로그만 남기고 계속 진행
					log.debug("댓글 저장 실패 (중복 등): {} - {}", comment.getYoutubeCommentId(), e.getMessage());
				}
			}
			if (savedCount > 0) {
				log.info("[댓글 Sync] {} - 신규 댓글 {}개 저장", video.getTitle(), savedCount);
			}
		}
	}

	/**
	 * [관리자용] 기간 내 상위 인기 영상들의 댓글을 동기화합니다.
	 * - 대상: 최근 days일 이내 업로드된 영상 중 조회수 Top 20 & 좋아요 Top 20
	 * - 범위: 각 영상당 댓글 최대 50개 (API 1회 호출)
	 * - 비용: 최대 40 unit (중복 제외 시 더 적음)
	 */
	@Transactional
	public void syncTopVideosComments(int days) {
		LocalDateTime searchAfter = LocalDateTime.now(ZONE_KST).minusDays(days);

		// 1. 조회수 Top 20
		List<Video> topViews = videoRepository.findTop20ByPublishedAtAfterOrderByViewCountDesc(searchAfter);
		// 2. 좋아요 Top 20
		List<Video> topLikes = videoRepository.findTop20ByPublishedAtAfterOrderByLikeCountDesc(searchAfter);

		// 3. 중복 제거 (Set)
		Set<Video> targetVideos = new java.util.HashSet<>();
		targetVideos.addAll(topViews);
		targetVideos.addAll(topLikes);

		log.info("### [Admin] 인기 영상 댓글 동기화 시작 (기간: 최근 {}일, 대상: {}개) ###", days, targetVideos.size());

		int successCount = 0;
		for (Video video : targetVideos) {
			try {
				// 이미 1회 호출 시 50개 제한이 걸려있으므로 그대로 사용
				syncCommentsForVideo(video);
				successCount++;
			} catch (Exception e) {
				log.error("댓글 동기화 실패: {} (ID: {})", video.getTitle(), video.getYoutubeVideoId(), e);
			}
		}

		log.info("### [Admin] 인기 영상 댓글 동기화 완료 (성공: {}/{}) ###", successCount, targetVideos.size());
	}

	/**
	 * 특정 시점(publishedAfter) 이후에 게시된 비디오들의 통계(조회수, 좋아요 등)를 갱신합니다.
	 * Search API를 사용하지 않고 DB에 있는 ID로 Videos API만 호출하므로 Quota 소모가 적습니다.
	 */
	@Transactional
	public void syncVideoStatisticsByPublishedAfter(LocalDateTime publishedAfter) {
		List<Video> targetVideos = videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(publishedAfter);

		if (targetVideos.isEmpty()) {
			return;
		}

		log.info("### 영상 통계 갱신 시작 (기준: {} 이후, 대상: {}개) ###", publishedAfter, targetVideos.size());

		// 50개씩 처리 (Youtube API 제한)
		int batchSize = 50;
		for (int i = 0; i < targetVideos.size(); i += batchSize) {
			List<Video> batch = targetVideos.subList(i, Math.min(i + batchSize, targetVideos.size()));
			updateBatchVideoStatistics(batch);
		}
	}

	private void updateBatchVideoStatistics(List<Video> videos) {
		List<String> videoIds = videos.stream()
				.map(Video::getYoutubeVideoId)
				.toList();

		YoutubeVideoResponse response = youtubeService.getVideoDetails(videoIds);

		if (response == null || response.items() == null) {
			return;
		}

		Map<String, YoutubeVideoResponse.VideoItem> responseMap = response.items().stream()
				.collect(Collectors.toMap(YoutubeVideoResponse.VideoItem::id, item -> item));

		for (Video video : videos) {
			YoutubeVideoResponse.VideoItem item = responseMap.get(video.getYoutubeVideoId());
			if (item != null && item.statistics() != null) {
				video.updateStatistics(
						parseCount(item.statistics().viewCount()),
						parseCount(item.statistics().likeCount()),
						parseCount(item.statistics().commentCount()));
				// 통계 갱신 시 댓글도 함께 동기화 (Gap 최소화)
				try {
					syncCommentsForVideo(video);
				} catch (Exception e) {
					log.error("통계 갱신 중 댓글 동기화 실패: {} (ID: {})", video.getTitle(), video.getYoutubeVideoId(), e);
				}
			}
		}
	}

	public void subscribeAllChannels(String callbackBaseUrl) {
		List<Channel> channels = channelRepository.findAll();
		String callbackUrl = callbackBaseUrl + "/api/youtube/webhook";

		int failed = 0;
		for (Channel channel : channels) {
			if (!youtubeService.subscribeToChannel(channel.getYoutubeChannelId(), callbackUrl)) {
				failed++;
			}
		}
		if (failed > 0) {
			log.warn("[youtube-pubsub] 구독 실패 {}/{} 채널", failed, channels.size());
		}
		// 전체 실패만 예외로 올린다 — 새벽 4시 갱신의 recordFailure(디스코드 알림)와 연결.
		// 일부 실패는 다음 날 갱신에서 만회되므로 경고만 남긴다.
		if (!channels.isEmpty() && failed == channels.size()) {
			throw new IllegalStateException("유튜브 PubSub 구독 전체 실패 (" + failed + "채널)");
		}
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
