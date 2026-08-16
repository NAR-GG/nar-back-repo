package com.toy.nar.app.youtube;

import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 영상 하나가 실패해도 나머지가 계속 처리되는지 검증한다.
 * <p>
 * 24시간 창의 영상 전부를 정각마다 도는 구조라, 삭제·비공개된 영상 하나 때문에 루프가 멈추면
 * 그 뒤 영상의 댓글이 통째로 누락된다.
 */
class YoutubeSyncCommentFailureTest {

	private YoutubeService youtubeService;
	private VideoRepository videoRepository;
	private YoutubeSyncService service;

	@BeforeEach
	void setUp() {
		youtubeService = mock(YoutubeService.class);
		videoRepository = mock(VideoRepository.class);

		service = new YoutubeSyncService(
				youtubeService, mock(com.toy.nar.domain.youtube.repository.ChannelRepository.class),
				videoRepository, mock(com.toy.nar.domain.youtube.repository.CommentRepository.class),
				mock(YoutubeChannelSyncTxService.class), mock(com.toy.nar.common.util.YoutubeProperties.class),
				mock(org.springframework.transaction.PlatformTransactionManager.class));
	}

	private Video video(String id) {
		Video v = mock(Video.class);
		when(v.getYoutubeVideoId()).thenReturn(id);
		when(v.getTitle()).thenReturn("제목 " + id);
		return v;
	}

	private WebClientResponseException notFound() {
		return WebClientResponseException.create(
				HttpStatus.NOT_FOUND.value(), "Not Found", HttpHeaders.EMPTY,
				new byte[0], StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("삭제된 영상(404)이 섞여 있어도 나머지 영상은 계속 동기화된다")
	void notFoundVideo_doesNotStopTheLoop() {
		// 스터빙 인자 안에서 video() 를 부르면 mock 설정이 중첩돼 Mockito 가 거부한다.
		List<Video> videos = List.of(video("ok-1"), video("gone"), video("ok-2"));
		when(videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(any())).thenReturn(videos);

		when(youtubeService.getVideoComments(anyString())).thenReturn(null);
		when(youtubeService.getVideoComments("gone")).thenThrow(notFound());

		assertThatCode(() -> service.syncRecentComments()).doesNotThrowAnyException();

		// 실패한 영상 뒤의 영상까지 호출됐는지 — 루프가 중간에 끊기지 않았다는 뜻이다.
		verify(youtubeService, times(1)).getVideoComments("ok-1");
		verify(youtubeService, times(1)).getVideoComments("gone");
		verify(youtubeService, times(1)).getVideoComments("ok-2");
	}

	@Test
	@DisplayName("404 가 아닌 예외도 루프를 멈추지 않는다")
	void otherException_doesNotStopTheLoop() {
		List<Video> videos = List.of(video("boom"), video("ok"));
		when(videoRepository.findByPublishedAtAfterOrderByPublishedAtAscIdAsc(any())).thenReturn(videos);

		when(youtubeService.getVideoComments(anyString())).thenReturn(null);
		when(youtubeService.getVideoComments("boom")).thenThrow(new IllegalStateException("네트워크 끊김"));

		assertThatCode(() -> service.syncRecentComments()).doesNotThrowAnyException();

		verify(youtubeService, times(1)).getVideoComments("ok");
	}
}
