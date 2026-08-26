package com.toy.nar.app.crawledcommunity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NaverPostDto {
	private String title;
	private String author;
	private String createdAt; // yyyy-MM-dd HH:mm:ss
	private String postUrl;
	private int voteCount;    // buff
	private int commentCount;
	private int viewCount;    // readCount
}
