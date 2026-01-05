package com.toy.nar.app.community.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NaverNewsDto {
	private String title;
	private String subContent;
	private String thumbnail;
	private String postUrl;    // linkUrl
	private String officeName;
	private long createdAt;    // timestamp
}
