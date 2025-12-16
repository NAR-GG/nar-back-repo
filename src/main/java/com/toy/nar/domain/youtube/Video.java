package com.toy.nar.domain.youtube;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "video")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "video_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "channel_id")
	private Channel channel;

	@Column(name = "youtube_video_id", nullable = false, unique = true)
	private String youtubeVideoId;

	private String title;

	private String thumbnailUrl;

	private String videoUrl;

	private LocalDateTime publishedAt;

	@Builder.Default
	private Long viewCount = 0L;

	@Builder.Default
	private Long likeCount = 0L;

	@Builder.Default
	private Long commentCount = 0L;

	public void updateStatistics(Long viewCount, Long likeCount, Long commentCount) {
		this.viewCount = viewCount;
		this.likeCount = likeCount;
		this.commentCount = commentCount;
	}

	public void updateInfo(String title, String thumbnailUrl) {
		this.title = title;
		this.thumbnailUrl = thumbnailUrl;
	}
}
