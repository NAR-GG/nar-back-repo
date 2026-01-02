package com.toy.nar.app.community.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvenPostDto {
	private String title;       // 제목
	private String author;      // 작성자
	private String createdAt;   // 작성일
	private String postUrl;     // 링크

	// 새로 추가된 필드
	private int voteCount;      // 추천수
	private int commentCount;   // 댓글수
	private int viewCount;      // 조회수
}
