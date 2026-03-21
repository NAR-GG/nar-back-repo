package com.toy.nar.app.youtube;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
	private YoutubeChannelSyncTxService youtubeChannelSyncTxService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private YoutubeSyncService youtubeSyncService;

	@Test
	@DisplayName("주간 동기화는 외부 API 수집 후 채널 단위 payload를 트랜잭션 서비스로 전달한다")
	void syncLastWeekVideos_collectsChannelVideosOutsideTransaction() {
		Channel channel = testChannel();
		YoutubePlaylistResponse playlistResponse = playlistResponse("video123");
		VideoItem videoItem = videoItem("video123", "Test Video Title", "1000", "50", "10",
				OffsetDateTime.now().toString());
		YoutubeVideoResponse videoResponse = new YoutubeVideoResponse(List.of(videoItem));

		when(channelRepository.findAll()).thenReturn(List.of(channel));
		when(youtubeService.getPlaylistItems(eq("UPLOADS"), any())).thenReturn(playlistResponse);
		when(youtubeService.getVideoDetails(List.of("video123"))).thenReturn(videoResponse);
		when(youtubeChannelSyncTxService.applyChannelVideos(any())).thenReturn(1);

		youtubeSyncService.syncLastWeekVideos();

		ArgumentCaptor<ChannelVideoSyncPayload> payloadCaptor = ArgumentCaptor.forClass(ChannelVideoSyncPayload.class);
		verify(youtubeChannelSyncTxService).applyChannelVideos(payloadCaptor.capture());

		ChannelVideoSyncPayload payload = payloadCaptor.getValue();
		org.junit.jupiter.api.Assertions.assertEquals("UC_test_channel", payload.channelYoutubeId());
		org.junit.jupiter.api.Assertions.assertEquals(List.of("video123"),
				payload.items().stream().map(VideoItem::id).toList());
		verify(videoRepository, never()).findByYoutubeVideoIdInOrderByPublishedAtAscIdAsc(any());
	}

	@Test
	@DisplayName("주간 동기화는 페이지별 응답을 채널 단위로 모은 뒤 한 번만 반영한다")
	void syncLastWeekVideos_collectsAllChannelPagesBeforeApply() {
		Channel channel = testChannel();
		YoutubePlaylistResponse firstPage = playlistResponseWithNextToken("NEXT", "videoA", "videoB");
		YoutubePlaylistResponse secondPage = playlistResponse("videoC");
		YoutubeVideoResponse firstDetails = new YoutubeVideoResponse(List.of(
				videoItem("videoA", "Video A", "1000", "50", "10", OffsetDateTime.now().toString()),
				videoItem("videoB", "Video B", "2000", "60", "20", OffsetDateTime.now().toString())));
		YoutubeVideoResponse secondDetails = new YoutubeVideoResponse(List.of(
				videoItem("videoC", "Video C", "3000", "70", "30", OffsetDateTime.now().toString())));

		when(channelRepository.findAll()).thenReturn(List.of(channel));
		when(youtubeService.getPlaylistItems("UPLOADS", null)).thenReturn(firstPage);
		when(youtubeService.getPlaylistItems("UPLOADS", "NEXT")).thenReturn(secondPage);
		when(youtubeService.getVideoDetails(List.of("videoA", "videoB"))).thenReturn(firstDetails);
		when(youtubeService.getVideoDetails(List.of("videoC"))).thenReturn(secondDetails);
		when(youtubeChannelSyncTxService.applyChannelVideos(any())).thenReturn(3);

		youtubeSyncService.syncLastWeekVideos();

		ArgumentCaptor<ChannelVideoSyncPayload> payloadCaptor = ArgumentCaptor.forClass(ChannelVideoSyncPayload.class);
		verify(youtubeChannelSyncTxService).applyChannelVideos(payloadCaptor.capture());
		List<String> collectedIds = payloadCaptor.getValue().items().stream().map(VideoItem::id).toList();
		org.junit.jupiter.api.Assertions.assertEquals(3, collectedIds.size());
		org.junit.jupiter.api.Assertions.assertTrue(collectedIds.containsAll(List.of("videoA", "videoB", "videoC")));
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
		return playlistResponseWithNextToken(null, videoIds);
	}

	private YoutubePlaylistResponse playlistResponseWithNextToken(String nextPageToken, String... videoIds) {
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

		return new YoutubePlaylistResponse(nextPageToken, null, null, items);
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
