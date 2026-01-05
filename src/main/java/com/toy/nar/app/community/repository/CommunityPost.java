package com.toy.nar.app.community.repository;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_post", indexes = {
	@Index(name = "idx_community_type_date", columnList = "communityType, createdAt DESC"),
	@Index(name = "idx_post_url", columnList = "postUrl", unique = true)
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CommunityPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CommunityType communityType;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String author;

	@Column(nullable = false, unique = true)
	private String postUrl; // 게시글 고유 URL (중복 방지용)

	private LocalDateTime createdAt; // 작성 시간

	// 통계 정보 (지속적으로 업데이트됨)
	private int viewCount;
	private int voteCount;
	private int commentCount;

	private LocalDateTime lastUpdated; // 데이터 수집(갱신) 시간

	// 데이터 갱신을 위한 편의 메서드
	public void updateStatistics(int viewCount, int voteCount, int commentCount) {
		this.viewCount = viewCount;
		this.voteCount = voteCount;
		this.commentCount = commentCount;
		this.lastUpdated = LocalDateTime.now();
	}
}
