package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PathVariable;

import com.toy.nar.app.youtube.CommentService;
import com.toy.nar.app.youtube.VideoService;
import com.toy.nar.app.youtube.YoutubeSyncService;
import com.toy.nar.app.youtube.dto.CommentResponse;
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
	private final CommentService commentService;

	@Operation(summary = "최신 영상 목록 조회", description = "카테고리, 정렬, 기간별로 영상을 조회합니다.")
	@GetMapping("/api/story/videos")
	public ResponseEntity<Page<VideoListResponse>> getVideos(
		@Parameter(description = "카테고리 (all: 전체, pro: 프로팀, shorts: 쇼츠 채널)", example = "all")
		@RequestParam(defaultValue = "all") String category,

		@Parameter(description = "정렬 기준 (latest: 최신순, views: 조회수순, likes: 좋아요순)", example = "latest")
		@RequestParam(defaultValue = "latest") String sort,

		@Parameter(description = "기간 필터 (all: 전체, week: 최근 1주, month: 최근 1달)", example = "all")
		@RequestParam(defaultValue = "all") String period,

		@Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
		@RequestParam(defaultValue = "0") int page,

		@Parameter(description = "페이지 크기", example = "20")
		@RequestParam(defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(page, size);
		
		return ResponseEntity.ok(videoService.getVideos(category, sort, period, pageable));
	}

	@Operation(summary = "영상 댓글 조회", description = "특정 영상의 댓글을 최신순 또는 인기순으로 조회합니다.")
	@GetMapping("/api/story/videos/{videoId}/comments")
	public ResponseEntity<Page<CommentResponse>> getVideoComments(
		@Parameter(description = "유튜브 비디오 ID") @PathVariable String videoId,
		@Parameter(description = "정렬 기준 (recent: 최신순, popular: 인기순)", example = "recent")
		@RequestParam(defaultValue = "recent") String sort,
		@Parameter(description = "페이징 정보 (기본 20개)") @PageableDefault(size = 20) Pageable pageable
	) {
		return ResponseEntity.ok(commentService.getComments(videoId, sort, pageable));
	}

	@Hidden
	@PostMapping("/api/story/sync")
	public ResponseEntity<String> syncLatestShorts() {
		youtubeSyncService.syncLastWeekVideos();
		return ResponseEntity.ok("Shorts synchronization completed for last week.");
	}

	@Hidden
	@Operation(summary = "[관리자용] 유튜브 채널 세팅", description = "유튜브 채널 ID 세팅을 초기화합니다.")
	@PostMapping("/api/youtube/sync")
	public ResponseEntity<String> syncLatestVideos() {
		youtubeSyncService.initChannelsFromProperties();
		return ResponseEntity.ok("Video synchronization completed for last week.");
	}

	@Hidden
	@Operation(summary = "[관리자용] 최근 1달 영상 동기화", description = "최근 30일간의 영상 데이터를 수집합니다 (초기 세팅용).")
	@PostMapping("/api/youtube/sync/month")
	public ResponseEntity<String> syncLatestMonthVideos() {
		youtubeSyncService.syncLastMonthVideos();
		return ResponseEntity.ok("Video synchronization completed for last month (30 days).");
	}

	@Hidden
	@Operation(summary = "[관리자용] 댓글 동기화 (최근 24시간 영상)", description = "최근 24시간 내 업로드된 영상들의 댓글을 수집합니다.")
	@PostMapping("/api/youtube/comments/sync")
	public ResponseEntity<String> syncRecentComments() {
		youtubeSyncService.syncRecentComments();
		return ResponseEntity.ok("Recent comments synchronization started.");
	}

	@Hidden
	@Operation(summary = "[관리자용] 인기 영상 댓글 동기화 (최근 1주일)", description = "최근 7일간 조회수/좋아요 상위 영상들의 댓글(최대 50개)을 수집합니다.")
	@PostMapping("/api/youtube/comments/sync/top-week")
	public ResponseEntity<String> syncTopWeekComments() {
		youtubeSyncService.syncTopVideosComments(7);
		return ResponseEntity.ok("Top videos comments synchronization (Last Week) started.");
	}

	@Hidden
	@Operation(summary = "[관리자용] 인기 영상 댓글 동기화 (최근 1달)", description = "최근 30일간 조회수/좋아요 상위 영상들의 댓글(최대 50개)을 수집합니다.")
	@PostMapping("/api/youtube/comments/sync/top-month")
	public ResponseEntity<String> syncTopMonthComments() {
		youtubeSyncService.syncTopVideosComments(30);
		return ResponseEntity.ok("Top videos comments synchronization (Last Month) started.");
	}
}

