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

import lombok.RequiredArgsConstructor;

@Profile({"local", "dev"})
@RestController
@RequiredArgsConstructor
public class YoutubeController {

	private final YoutubeSyncService youtubeSyncService;
	private final VideoService videoService;

	@GetMapping("/api/youtube/videos")
	public ResponseEntity<Page<VideoListResponse>> getVideos(
		@RequestParam(defaultValue = "all") String category,
		@PageableDefault(size = 20) Pageable pageable
	) {
		return ResponseEntity.ok(videoService.getVideosByCategory(category, pageable));
	}

	@PostMapping("/api/youtube/sync")
	public ResponseEntity<String> syncLatestShorts() {
		youtubeSyncService.syncLastWeekShorts();
		return ResponseEntity.ok("Shorts synchronization completed for last week.");
	}
}

