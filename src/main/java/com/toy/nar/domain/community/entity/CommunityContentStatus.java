package com.toy.nar.domain.community.entity;

/**
 * 글·댓글·이미지 공용 노출 상태. DELETED 는 작성자 소프트 삭제, HIDDEN 은 운영 블라인드.
 * TEST 는 글에만 쓰는 상태 — 테스터에게만 보이는 prod 확인용 글이다
 * ({@code community.tester-member-ids}).
 */
public enum CommunityContentStatus {
	VISIBLE, HIDDEN, DELETED, TEST
}
