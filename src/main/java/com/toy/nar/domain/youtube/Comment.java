package com.toy.nar.domain.youtube;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "comment")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "comment_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "video_id", nullable = false)
	@ToString.Exclude
	private Video video;

	@Column(name = "youtube_comment_id", nullable = false, unique = true)
	private String youtubeCommentId;

	@Column(name = "author_display_name")
	private String authorDisplayName;

	@Column(name = "author_profile_image_url")
	private String authorProfileImageUrl;

	@Lob
	@Column(name = "text_display", columnDefinition = "TEXT")
	private String textDisplay;

	@Column(name = "like_count")
	private Long likeCount;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;
}
