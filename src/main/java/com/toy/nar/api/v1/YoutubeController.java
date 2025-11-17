package com.toy.nar.api.v1;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.youtube.YoutubeService;
import com.toy.nar.app.youtube.dto.YoutubeVideoDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class YoutubeController {

	private final YoutubeService youtubeService;

	// 최신순
	@GetMapping("/api/youtube/shorts/latest")
	public List<YoutubeVideoDto> getShortsLatest(
		@RequestParam(defaultValue = "UCBjjh72yM1KoMHfFhNBQr8w") String channelId,
		@RequestParam(defaultValue = "20") long maxResults
	) {
		return youtubeService.getChannelShortsOrderByLatest(channelId, maxResults);
	}

	// 댓글순
	@GetMapping("/api/youtube/shorts/commented")
	public List<YoutubeVideoDto> getShortsByComments(
		@RequestParam(defaultValue = "UCBjjh72yM1KoMHfFhNBQr8w") String channelId,
		@RequestParam(defaultValue = "20") long maxResults
	) {
		return youtubeService.getChannelShortsOrderByComments(channelId, maxResults);
	}
}

