package com.toy.nar.domain.community.repository;

/** 첨부 사진 한 행. id 는 IMAGE 신고의 target_id 가 된다. */
public record CommunityPostImageRow(long id, long postId, String imageUrl) {
}
