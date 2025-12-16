package com.toy.nar.app.youtube;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.SearchId;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.SearchItem;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.SearchSnippet;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.Thumbnail;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoItem;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoStatistics;
import com.toy.nar.common.util.YoutubeProperties;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
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
	private YoutubeProperties youtubeProperties;

	@InjectMocks
	private YoutubeSyncService youtubeSyncService;

	@Test
	@DisplayName("syncSingleChannelVideos는 통계 정보가 포함된 비디오를 저장해야 한다")
	void syncSingleChannelVideos_shouldSaveVideoWithStatistics() {
		// Given
		Channel channel = Channel.builder()
			.youtubeChannelId("UC_test_channel")
			.channelName("Test Channel")
			.channelType(ChannelType.PRO_TEAMS)
			.build();

		// 1. Mock Search Response
		SearchSnippet searchSnippet = new SearchSnippet(
			OffsetDateTime.now().toString(),
			"UC_test_channel",
			"Test Video Title",
			"Description",
			Map.of("default", new Thumbnail("http://thumb.url", 120, 90))
		);
		SearchId searchId = new SearchId("youtube#video", "video123");
		SearchItem searchItem = new SearchItem(searchId, searchSnippet);
		YoutubeSearchResponse searchResponse = new YoutubeSearchResponse(null, null, null, List.of(searchItem));

		when(youtubeService.searchLatestVideos(eq("UC_test_channel"), anyLong(), any(), any())).thenReturn(searchResponse);

		// 2. Mock Details Response (with Statistics)
		VideoStatistics statistics = new VideoStatistics("1000", "50", "10");
		VideoItem videoItem = new VideoItem("video123", searchSnippet, statistics);
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(videoItem));

		when(youtubeService.getVideoDetails(List.of("video123"))).thenReturn(videoResponse);
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
		when(videoRepository.findByYoutubeVideoId("video123")).thenReturn(Optional.empty()); // New video

		// When
		when(channelRepository.findAll()).thenReturn(List.of(channel));
		youtubeSyncService.syncLastWeekVideos();

		// Then
		verify(videoRepository).saveAll(anyList());
		verify(youtubeService).getVideoDetails(List.of("video123"));
	}

	@Test
	@DisplayName("syncSingleChannelVideos는 이미 존재하는 영상의 통계 정보를 업데이트해야 한다")
	void syncSingleChannelVideos_shouldUpdateExistingVideoStatistics() {
		// Given
		Channel channel = Channel.builder()
			.youtubeChannelId("UC_test_channel")
			.channelName("Test Channel")
			.channelType(ChannelType.PRO_TEAMS)
			.build();

		SearchSnippet searchSnippet = new SearchSnippet(
			OffsetDateTime.now().toString(),
			"UC_test_channel",
			"Test Video Title",
			"Description",
			Map.of("default", new Thumbnail("http://thumb.url", 120, 90))
		);
		SearchId searchId = new SearchId("youtube#video", "video123");
		SearchItem searchItem = new SearchItem(searchId, searchSnippet);
		YoutubeSearchResponse searchResponse = new YoutubeSearchResponse(null, null, null, List.of(searchItem));

		when(youtubeService.searchLatestVideos(eq("UC_test_channel"), anyLong(), any(), any())).thenReturn(searchResponse);

		VideoStatistics statistics = new VideoStatistics("5000", "100", "20"); // Updated stats
		VideoItem videoItem = new VideoItem("video123", searchSnippet, statistics);
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(videoItem));

		when(youtubeService.getVideoDetails(List.of("video123"))).thenReturn(videoResponse);
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");

		// Mock Existing Video
		Video existingVideo = mock(Video.class);
		when(videoRepository.findByYoutubeVideoId("video123")).thenReturn(Optional.of(existingVideo));

		when(channelRepository.findAll()).thenReturn(List.of(channel));

		// When
		youtubeSyncService.syncLastWeekVideos();

		// Then
		// Verify updateStatistics was called with new values
		verify(existingVideo).updateStatistics(5000L, 100L, 20L);
		verify(videoRepository).saveAll(anyList());
	}
}
