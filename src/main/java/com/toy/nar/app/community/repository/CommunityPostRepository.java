package com.toy.nar.app.community.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
	// URL로 게시글 찾기 (중복 확인 및 업데이트용)
	Optional<CommunityPost> findByPostUrl(String postUrl);

	// 최신순 상위 N개
	List<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

	// 인기순(추천수) 상위 N개
	List<CommunityPost> findAllByOrderByVoteCountDesc(Pageable pageable);

	// 특정 날짜 이전 데이터 삭제
	void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
