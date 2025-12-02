package com.toy.nar.app.youtube;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncService {

	private final YoutubeService youtubeService;
	private final ChannelRepository channelRepository;
	private final VideoRepository videoRepository;

	// 한국 시간대 상수 정의
	private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

	@Transactional
	public void syncLastWeekShorts() {
		List<Channel> channels = channelRepository.findAll();

		if (channels.isEmpty()) {
			log.info("### 동기화할 채널이 없습니다. ###");
			return;
		}

		// 기준 시간: 현재(KST)로부터 7일 전
		LocalDateTime oneWeekAgo = LocalDateTime.now(ZONE_KST).minusDays(7);
		log.info("### Sync 시작. 기준 시간(KST): {} (대상 채널: {}개) ###", oneWeekAgo, channels.size());

		int totalSaved = 0;

		for (Channel channel : channels) {
			try {
				totalSaved += syncSingleChannel(channel, oneWeekAgo);
			} catch (Exception e) {
				log.error("채널 동기화 실패: {} ({})", channel.getChannelName(), channel.getYoutubeChannelId(), e);
			}
		}

		log.info("### 전체 동기화 완료. 총 {}개의 영상이 저장되었습니다. ###", totalSaved);
	}

	private int syncSingleChannel(Channel channel, LocalDateTime since) {
		// 1. ChannelType에 따라 검색 조건(Duration) 분기
		String videoDurationParam = null;
		if (channel.getChannelType() == ChannelType.SHORTS) {
			videoDurationParam = "short";
		}

		// 2. 영상 검색
		YoutubeSearchResponse searchResponse = youtubeService.searchLatestVideos(
			channel.getYoutubeChannelId(),
			20,
			videoDurationParam
		);

		if (searchResponse == null || searchResponse.items() == null) {
			return 0;
		}

		// 3. 필터링 및 변환
		List<Video> videosToSave = searchResponse.items().stream()
			// (1) 날짜 필터링
			.filter(item -> {
				// API 시간(UTC)을 파싱 후 한국 시간(KST)으로 변환하여 비교
				OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
				LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();
				return publishedAtKst.isAfter(since);
			})
			// (2) 중복 필터링
			.filter(item -> !videoRepository.existsByYoutubeVideoId(item.id().videoId()))
			// (3) 엔티티 변환
			.map(item -> convertToVideoEntity(item, channel))
			.toList();

		if (!videosToSave.isEmpty()) {
			videoRepository.saveAll(videosToSave);
			log.info("[{}] ({}) 신규 영상 {}개 저장 완료",
				channel.getChannelName(), channel.getChannelType(), videosToSave.size());
			return videosToSave.size();
		}

		return 0;
	}

	private Video convertToVideoEntity(YoutubeSearchResponse.SearchItem item, Channel channel) {
		String videoId = item.id().videoId();
		String title = item.snippet().title();
		String thumbnailUrl = youtubeService.extractBestThumbnailUrl(item.snippet().thumbnails());

		// UTC OffsetDateTime -> KST LocalDateTime 변환 로직
		OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
		LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

		// ChannelType에 따른 URL 생성 분기
		String videoUrl;
		if (channel.getChannelType() == ChannelType.SHORTS) {
			videoUrl = "https://www.youtube.com/shorts/" + videoId;
		} else {
			videoUrl = "https://www.youtube.com/watch?v=" + videoId;
		}

		return Video.builder()
			.channel(channel)
			.youtubeVideoId(videoId)
			.title(title)
			.thumbnailUrl(thumbnailUrl)
			.videoUrl(videoUrl)
			.publishedAt(publishedAtKst) // 한국 시간으로 저장
			.build();
	}
}