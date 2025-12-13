package com.toy.nar.domain.youtube.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.toy.nar.domain.youtube.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
	boolean existsByYoutubeCommentId(String youtubeCommentId);
}
