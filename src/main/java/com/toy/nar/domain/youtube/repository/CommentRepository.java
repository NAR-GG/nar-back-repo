package com.toy.nar.domain.youtube.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.domain.youtube.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
	boolean existsByYoutubeCommentId(String youtubeCommentId);

	List<Comment> findByYoutubeCommentIdIn(List<String> youtubeCommentIds);

	Page<Comment> findByVideo_YoutubeVideoId(String videoId, Pageable pageable);

	@Modifying
	@Query(value = """
			INSERT IGNORE INTO comment
			(video_id, youtube_comment_id, author_display_name, author_profile_image_url, text_display, like_count, published_at)
			VALUES (:videoId, :youtubeCommentId, :authorDisplayName, :authorProfileImageUrl, :textDisplay, :likeCount, :publishedAt)
			""", nativeQuery = true)
	int insertIgnore(
			@Param("videoId") Long videoId,
			@Param("youtubeCommentId") String youtubeCommentId,
			@Param("authorDisplayName") String authorDisplayName,
			@Param("authorProfileImageUrl") String authorProfileImageUrl,
			@Param("textDisplay") String textDisplay,
			@Param("likeCount") Long likeCount,
			@Param("publishedAt") java.time.LocalDateTime publishedAt);
}
