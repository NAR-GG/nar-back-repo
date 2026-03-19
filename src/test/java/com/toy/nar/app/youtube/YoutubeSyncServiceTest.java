package com.toy.nar.app.youtube;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.toy.nar.app.youtube.dto.YoutubePlaylistResponse;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.Thumbnail;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoItem;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoStatistics;
import com.toy.nar.common.util.YoutubeProperties;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.CommentRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

@ExtendWith(MockitoExtension.class)
class YoutubeSyncServiceTest {

	@Mock
	private YoutubeService youtubeService;

	@Mock
	private ChannelRepository channelRepository;

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private CommentRepository commentRepository;

	@Mock
	private YoutubeProperties youtubeProperties;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private YoutubeSyncService youtubeSyncService;

	@Test
	@DisplayName("주간 동기화는 youtubeVideoId IN 조회로 기존 영상을 한 번에 가져온다")
	void syncLastWeekVideos_fetchesExistingVideosWithSingleInQuery() {
		Channel channel = testChannel();
		YoutubePlaylistResponse playlistResponse = playlistResponse("video123");
		VideoItem videoItem = videoItem("video123", "Test Video Title", "1000", "50", "10",
				OffsetDateTime.now().toString());
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(videoItem));

		when(channelRepository.findAll()).thenReturn(List.of(channel));
		when(youtubeService.getPlaylistItems(eq("UPLOADS"), any())).thenReturn(playlistResponse);
		when(youtubeService.getVideoDetails(List.of("video123"))).thenReturn(videoResponse);
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
		when(videoRepository.findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(List.of("video123")))
				.thenReturn(List.of());

		youtubeSyncService.syncLastWeekVideos();

		verify(videoRepository).findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(List.of("video123"));
		verify(videoRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("주간 동기화는 정렬된 기존 영상 순서대로 업데이트한다")
	void syncLastWeekVideos_updatesExistingVideosInRepositoryOrder() {
		Channel channel = testChannel();
		YoutubePlaylistResponse playlistResponse = playlistResponse("videoA", "videoB");
		VideoItem olderItem = videoItem("videoA", "Older Video", "1000", "50", "10",
				OffsetDateTime.parse("2026-03-18T09:00:00Z").toString());
		VideoItem newerItem = videoItem("videoB", "Newer Video", "5000", "100", "20",
				OffsetDateTime.parse("2026-03-18T11:00:00Z").toString());
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(newerItem, olderItem));

		Video existingOlderVideo = mock(Video.class);
		when(existingOlderVideo.getYoutubeVideoId()).thenReturn("videoA");
		Video existingNewerVideo = mock(Video.class);
		when(existingNewerVideo.getYoutubeVideoId()).thenReturn("videoB");

		when(channelRepository.findAll()).thenReturn(List.of(channel));
		when(youtubeService.getPlaylistItems(eq("UPLOADS"), any())).thenReturn(playlistResponse);
		when(youtubeService.getVideoDetails(List.of("videoA", "videoB"))).thenReturn(videoResponse);
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
		when(videoRepository.findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(anyList()))
				.thenReturn(List.of(existingOlderVideo, existingNewerVideo));

		youtubeSyncService.syncLastWeekVideos();

		InOrder inOrder = inOrder(existingOlderVideo, existingNewerVideo);
		inOrder.verify(existingOlderVideo).updateInfo("Older Video", "http://thumb.url");
		inOrder.verify(existingOlderVideo).updateStatistics(1000L, 50L, 10L);
		inOrder.verify(existingNewerVideo).updateInfo("Newer Video", "http://thumb.url");
		inOrder.verify(existingNewerVideo).updateStatistics(5000L, 100L, 20L);
		verify(videoRepository).saveAll(anyList());
	}

	private Channel testChannel() {
		return Channel.builder()
				.youtubeChannelId("UC_test_channel")
				.channelName("Test Channel")
				.uploadPlaylistId("UPLOADS")
				.channelType(ChannelType.PRO_TEAMS)
				.build();
	}

	private YoutubePlaylistResponse playlistResponse(String... videoIds) {
		List<YoutubePlaylistResponse.PlaylistItem> items = java.util.Arrays.stream(videoIds)
				.map(videoId -> new YoutubePlaylistResponse.PlaylistItem(
						"playlist-" + videoId,
						new YoutubePlaylistResponse.Snippet(
								OffsetDateTime.now().toString(),
								"UC_test_channel",
								"Title " + videoId,
								"Description",
								Map.of("default", new YoutubePlaylistResponse.Thumbnail("http://thumb.url", 120, 90)),
								new YoutubePlaylistResponse.ResourceId("youtube#video", videoId)),
						new YoutubePlaylistResponse.ContentDetails(videoId, OffsetDateTime.now().toString())))
				.toList();

		return new YoutubePlaylistResponse(null, null, null, items);
	}

	private VideoItem videoItem(String videoId, String title, String viewCount, String likeCount,
			String commentCount, String publishedAt) {
		return new VideoItem(
				videoId,
				new com.toy.nar.app.youtube.dto.YoutubeSearchResponse.SearchSnippet(
						publishedAt,
						"UC_test_channel",
						title,
						"Description",
						Map.of("default", new Thumbnail("http://thumb.url", 120, 90))),
				new VideoStatistics(viewCount, likeCount, commentCount));
	}
}
