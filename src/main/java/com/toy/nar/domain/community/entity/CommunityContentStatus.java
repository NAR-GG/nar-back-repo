package com.toy.nar.domain.community.entity;

/** 글·댓글·이미지 공용 노출 상태. DELETED 는 작성자 소프트 삭제, HIDDEN 은 운영 블라인드. */
public enum CommunityContentStatus {
	VISIBLE, HIDDEN, DELETED
}
