package com.toy.nar.app.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.toy.nar.domain.youtube.repository.CommentRepository;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	@Mock
	private CommentRepository commentRepository;

	@InjectMocks
	private CommentService commentService;

	@Test
	@DisplayName("댓글 조회 - 인기순 정렬 요청 시 likeCount 내림차순으로 조회되어야 한다")
	void getComments_popularSort() {
		// Given
		String videoId = "video123";
		Pageable pageable = PageRequest.of(0, 20);
		when(commentRepository.findByVideo_YoutubeVideoId(eq(videoId), any(Pageable.class)))
			.thenReturn(Page.empty());

		// When
		commentService.getComments(videoId, "popular", pageable);

		// Then
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(commentRepository).findByVideo_YoutubeVideoId(eq(videoId), pageableCaptor.capture());

		Pageable capturedPageable = pageableCaptor.getValue();
		Sort.Order order = capturedPageable.getSort().getOrderFor("likeCount");
		
		assertNotNull(order, "likeCount 정렬 옵션이 있어야 합니다");
		assertEquals(Sort.Direction.DESC, order.getDirection(), "내림차순 정렬이어야 합니다");
	}

	@Test
	@DisplayName("댓글 조회 - 최신순(기본) 정렬 요청 시 publishedAt 내림차순으로 조회되어야 한다")
	void getComments_recentSort() {
		// Given
		String videoId = "video123";
		Pageable pageable = PageRequest.of(0, 20);
		when(commentRepository.findByVideo_YoutubeVideoId(eq(videoId), any(Pageable.class)))
			.thenReturn(Page.empty());

		// When
		commentService.getComments(videoId, "recent", pageable);

		// Then
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(commentRepository).findByVideo_YoutubeVideoId(eq(videoId), pageableCaptor.capture());

		Pageable capturedPageable = pageableCaptor.getValue();
		Sort.Order order = capturedPageable.getSort().getOrderFor("publishedAt");

		assertNotNull(order, "publishedAt 정렬 옵션이 있어야 합니다");
		assertEquals(Sort.Direction.DESC, order.getDirection(), "내림차순 정렬이어야 합니다");
	}
}
