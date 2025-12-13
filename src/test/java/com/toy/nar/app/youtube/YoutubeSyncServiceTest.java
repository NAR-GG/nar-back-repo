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
		YoutubeSearchResponse searchResponse = new YoutubeSearchResponse(List.of(searchItem));

		when(videoRepository.findLatestPublishedAtByChannel(channel)).thenReturn(null);
		when(youtubeService.searchLatestVideos(eq("UC_test_channel"), anyLong(), any())).thenReturn(searchResponse);
		when(videoRepository.existsByYoutubeVideoId("video123")).thenReturn(false);

		// 2. Mock Details Response (with Statistics)
		VideoStatistics statistics = new VideoStatistics("1000", "50", "10");
		VideoItem videoItem = new VideoItem("video123", searchSnippet, statistics);
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(videoItem));

		when(youtubeService.getVideoDetails(List.of("video123"))).thenReturn(videoResponse);
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");

		// When
		youtubeSyncService.syncLastWeekVideos(); // This iterates over all channels, so we need to mock findAll

		// Wait, syncing calls findAll first.
		when(channelRepository.findAll()).thenReturn(List.of(channel));

		youtubeSyncService.syncLastWeekVideos();

		// Then
		verify(videoRepository).saveAll(anyList());
		
		// Verify details were fetched
		verify(youtubeService).getVideoDetails(List.of("video123"));
	}
}
