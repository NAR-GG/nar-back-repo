package com.toy.nar.domain.youtube.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

	boolean existsByYoutubeVideoId(String youtubeVideoId);

	Optional<Video> findByYoutubeVideoId(String youtubeVideoId);

	// 1. 전체 조회 (Channel 정보 함께 로딩하여 N+1 문제 방지)
	@EntityGraph(attributePaths = "channel")
	Page<Video> findAll(Pageable pageable);

	// 2. 카테고리(ChannelType)별 조회
	@EntityGraph(attributePaths = "channel")
	Page<Video> findByChannel_ChannelType(ChannelType channelType, Pageable pageable);
}