package com.toy.nar.app.youtube;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.CommentResponse;
import com.toy.nar.domain.youtube.repository.CommentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

	private final CommentRepository commentRepository;

	public Page<CommentResponse> getComments(String videoId, String sort, Pageable pageable) {
		Sort sorting = Sort.by(Sort.Direction.DESC, "publishedAt"); // Default: 최신순
		if ("popular".equalsIgnoreCase(sort)) {
			sorting = Sort.by(Sort.Direction.DESC, "likeCount"); // 인기순
		}

		Pageable sortedPageable = PageRequest.of(
			pageable.getPageNumber(),
			pageable.getPageSize(),
			sorting
		);

		return commentRepository.findByVideo_YoutubeVideoId(videoId, sortedPageable)
			.map(CommentResponse::from);
	}
}
