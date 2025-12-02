package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.youtube.VideoService;
import com.toy.nar.app.youtube.YoutubeSyncService;
import com.toy.nar.app.youtube.dto.VideoListResponse;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "5. 유튜브 스토리 서비스", description = "프로팀 및 쇼츠 채널의 최신 영상 데이터를 제공합니다.")
@RestController
@RequiredArgsConstructor
public class YoutubeController {

	private final YoutubeSyncService youtubeSyncService;
	private final VideoService videoService;

	@Operation(summary = "최신 영상 목록 조회", description = "전체, 프로팀, 쇼츠 카테고리별로 최신순 영상을 페이징 조회합니다.")
	@GetMapping("/api/story/videos")
	public ResponseEntity<Page<VideoListResponse>> getVideos(
		@Parameter(description = "카테고리 (all: 전체, pro: 프로팀, shorts: 쇼츠 채널)", example = "all")
		@RequestParam(defaultValue = "all") String category,

		@Parameter(description = "페이징 정보 (기본 20개)")
		@PageableDefault(size = 20) Pageable pageable
	) {
		return ResponseEntity.ok(videoService.getVideosByCategory(category, pageable));
	}

	@Hidden
	@PostMapping("/api/story/sync")
	public ResponseEntity<String> syncLatestShorts() {
		youtubeSyncService.syncLastWeekVideos();
		return ResponseEntity.ok("Shorts synchronization completed for last week.");
	}

	@Operation(summary = "[관리자용] 유튜브 채널 세팅", description = "유튜브 채널 ID 세팅을 초기화합니다.")
	@PostMapping("/api/youtube/sync")
	public ResponseEntity<String> syncLatestVideos() {
		youtubeSyncService.initChannelsFromProperties();
		return ResponseEntity.ok("Video synchronization completed for last week.");
	}
}

