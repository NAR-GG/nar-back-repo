package com.toy.nar.api.v3;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.youtube.YoutubeService;
import com.toy.nar.app.youtube.dto.YoutubeVideoDto;

import lombok.RequiredArgsConstructor;

@Profile({"local", "dev"})
@RestController
@RequiredArgsConstructor
public class YoutubeController {

	private final YoutubeService youtubeService;

	// 최신순
	@GetMapping("/api/youtube/shorts/latest")
	public List<YoutubeVideoDto> getShortsLatestMultiChannel(
		@RequestParam(required = false) List<String> channelIds,
		@RequestParam(defaultValue = "20") long limit
	) {
		// 파라미터 없으면 기본 3개 채널 사용
		List<String> defaultChannels = List.of(
			"UCORzxHO2quCHbE2fTo_snEg", // 롤뻔뻔
			"UCBjjh72yM1KoMHfFhNBQr8w", // 대유쾌마운틴
			"UCwvk_uRd0w-n0O1I4QRdM0g"  // 롤꺾마
		);

		List<String> targetChannels = (channelIds == null || channelIds.isEmpty())
			? defaultChannels
			: channelIds;

		return youtubeService.getChannelsShortsOrderByLatest(targetChannels, limit);
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

