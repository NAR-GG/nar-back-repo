package com.toy.nar.domain.youtube.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long>, JpaSpecificationExecutor<Video> {

	boolean existsByYoutubeVideoId(String youtubeVideoId);

	Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

	// 1. 전체 조회 (Channel 정보 함께 로딩하여 N+1 문제 방지)
	@EntityGraph(attributePaths = "channel")
	Page<Video> findAll(Pageable pageable);

	// 2. 카테고리(ChannelType)별 조회
	@EntityGraph(attributePaths = "channel")
	Page<Video> findByChannel_ChannelType(ChannelType channelType, Pageable pageable);

	// 3. Specification(동적 쿼리) 조회 시에도 Channel 패치 조인 적용
	@Override
	@EntityGraph(attributePaths = "channel")
	Page<Video> findAll(@Nullable Specification<Video> spec, Pageable pageable);

	@Query("SELECT MAX(v.publishedAt) FROM Video v WHERE v.channel = :channel")
	LocalDateTime findLatestPublishedAtByChannel(@Param("channel") Channel channel);

	List<Video> findByPublishedAtAfter(LocalDateTime publishedAt);

	// 기간 내 조회수 상위 20개
	List<Video> findTop20ByPublishedAtAfterOrderByViewCountDesc(LocalDateTime publishedAt);

	// 기간 내 좋아요 상위 20개
	List<Video> findTop20ByPublishedAtAfterOrderByLikeCountDesc(LocalDateTime publishedAt);
}