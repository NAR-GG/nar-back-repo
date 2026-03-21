package com.toy.nar.app.youtube;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.Thumbnail;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoItem;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoStatistics;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

@ExtendWith(MockitoExtension.class)
class YoutubeChannelSyncTxServiceTest {

	@Mock
	private ChannelRepository channelRepository;

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private YoutubeService youtubeService;

	@InjectMocks
	private YoutubeChannelSyncTxService youtubeChannelSyncTxService;

	@Test
	@DisplayName("채널 반영은 youtubeVideoId IN 조회로 기존 영상을 한 번에 가져온다")
	void applyChannelVideos_fetchesExistingVideosWithSingleInQuery() {
		Channel channel = testChannel();
		VideoItem videoItem = videoItem("video123", "Test Video Title", "1000", "50", "10",
				OffsetDateTime.now().toString());
		ChannelVideoSyncPayload payload = new ChannelVideoSyncPayload("UC_test_channel", List.of(videoItem));

		when(channelRepository.findByYoutubeChannelId("UC_test_channel")).thenReturn(java.util.Optional.of(channel));
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
		when(videoRepository.findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(List.of("video123")))
				.thenReturn(List.of());

		youtubeChannelSyncTxService.applyChannelVideos(payload);

		verify(videoRepository).findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(List.of("video123"));
		verify(videoRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("채널 반영은 정렬된 기존 영상 순서대로 업데이트한다")
	void applyChannelVideos_updatesExistingVideosInRepositoryOrder() {
		Channel channel = testChannel();
		VideoItem olderItem = videoItem("videoA", "Older Video", "1000", "50", "10",
				OffsetDateTime.parse("2026-03-18T09:00:00Z").toString());
		VideoItem newerItem = videoItem("videoB", "Newer Video", "5000", "100", "20",
				OffsetDateTime.parse("2026-03-18T11:00:00Z").toString());
		ChannelVideoSyncPayload payload = new ChannelVideoSyncPayload("UC_test_channel", List.of(newerItem, olderItem));

		Video existingOlderVideo = mock(Video.class);
		when(existingOlderVideo.getYoutubeVideoId()).thenReturn("videoA");
		Video existingNewerVideo = mock(Video.class);
		when(existingNewerVideo.getYoutubeVideoId()).thenReturn("videoB");

		when(channelRepository.findByYoutubeChannelId("UC_test_channel")).thenReturn(java.util.Optional.of(channel));
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
		when(videoRepository.findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(anyList()))
				.thenReturn(List.of(existingOlderVideo, existingNewerVideo));

		youtubeChannelSyncTxService.applyChannelVideos(payload);

		InOrder inOrder = inOrder(existingOlderVideo, existingNewerVideo);
		inOrder.verify(existingOlderVideo).updateInfo("Older Video", "http://thumb.url");
		inOrder.verify(existingOlderVideo).updateStatistics(1000L, 50L, 10L);
		inOrder.verify(existingNewerVideo).updateInfo("Newer Video", "http://thumb.url");
		inOrder.verify(existingNewerVideo).updateStatistics(5000L, 100L, 20L);
		verify(videoRepository).saveAll(anyList());
	}

	private Channel testChannel() {
		return Channel.builder()
				.id(1L)
				.youtubeChannelId("UC_test_channel")
				.channelName("Test Channel")
				.uploadPlaylistId("UPLOADS")
				.channelType(ChannelType.PRO_TEAMS)
				.build();
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
