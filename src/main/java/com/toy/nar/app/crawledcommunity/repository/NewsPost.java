package com.toy.nar.app.crawledcommunity.repository;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_post", indexes = {
	@Index(name = "idx_news_url", columnList = "postUrl", unique = true),
	@Index(name = "idx_news_date", columnList = "createdAt DESC")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NewsPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String subContent;

	private String thumbnail;

	@Column(nullable = false, unique = true)
	private String postUrl;

	private String officeName;

	private LocalDateTime createdAt;
	private LocalDateTime lastUpdated;

	public void update(String title, String subContent, String thumbnail) {
		this.title = title;
		this.subContent = subContent;
		this.thumbnail = thumbnail;
		this.lastUpdated = LocalDateTime.now();
	}
}