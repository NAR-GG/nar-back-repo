package com.toy.nar.domain.community.repository;

import java.time.LocalDateTime;

/** 목록·상세 조인 결과 한 행. 작성자·팀은 LEFT JOIN 이라 전부 null 가능. */
public record CommunityPostRow(
		long id,
		Long boardTeamId,
		/** 게시판 팀의 코드. 전체 게시판이면 null. 작성자 응원팀(authorTeamCode)과 다른 값이다. */
		String boardTeamCode,
		String title,
		String body,
		/** PLAIN / BLOCKS. BLOCKS 면 body 는 블록 JSON 이고 미리보기는 preview 컬럼이다. */
		String bodyFormat,
		String preview,
		int viewCount,
		int likeCount,
		int commentCount,
		String status,
		LocalDateTime createdAt,
		LocalDateTime editedAt,
		Long authorMemberId,
		String authorName,
		String authorTag,
		String authorProfileImageUrl,
		Long authorTeamId,
		String authorTeamCode,
		String authorTeamImageUrl,
		/** 이 글에 투표가 붙어 있는가 — 목록의 투표 배지용. */
		boolean hasPoll,
		Long scrapId) {
}
