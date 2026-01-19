package com.toy.nar.domain.youtube.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.youtube.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
	boolean existsByYoutubeCommentId(String youtubeCommentId);

	List<Comment> findByYoutubeCommentIdIn(List<String> youtubeCommentIds);

	Page<Comment> findByVideo_YoutubeVideoId(String videoId, Pageable pageable);
}
