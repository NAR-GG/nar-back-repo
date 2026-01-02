package com.toy.nar.app.community.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OpggPostDto {
	private Long id;
	private String title;
	private String author;
	private String createdAt;
	private String postUrl;

	private int voteCount;      // 추천수
	private int commentCount;   // 댓글 수
	private int viewCount;      // 조회수
}
