package com.toy.nar.app.youtube;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.youtube.dto.YoutubeSearchResponse;
import com.toy.nar.common.util.YoutubeProperties;
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
	private final YoutubeProperties youtubeProperties;

	private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

	/**
	 * [관리자용 1단계] 채널 목록 초기화 (YML -> DB)
	 * application.yml에 정의된 채널 중 DB에 없는 채널을 찾아 API로 정보를 가져와 저장합니다.
	 */
	@Transactional
	public int initChannelsFromProperties() {
		log.info("### [Admin] 채널 데이터 초기화(From YML) 시작 ###");

		// 1. yml 데이터 준비 (Map<Type, List<ID>> -> Map<ID, Type>)
		Map<String, ChannelType> idTypeMap = youtubeProperties.seedChannels().entrySet().stream()
			.flatMap(entry -> entry.getValue().stream()
				.map(id -> Map.entry(id, entry.getKey())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		List<String> allTargetIds = new ArrayList<>(idTypeMap.keySet());

		if (allTargetIds.isEmpty()) {
			log.warn("yml에 설정된 채널이 없습니다.");
			return 0;
		}

		// 2. 이미 DB에 존재하는 채널 확인 (중복 방지)
		Set<String> existingYoutubeIds = channelRepository.findByYoutubeChannelIdIn(allTargetIds).stream()
			.map(Channel::getYoutubeChannelId)
			.collect(Collectors.toSet());

		// 3. 없는 ID 필터링 (신규 저장 대상)
		List<String> newIds = allTargetIds.stream()
			.filter(id -> !existingYoutubeIds.contains(id))
			.toList();

		if (newIds.isEmpty()) {
			log.info("### 추가할 신규 채널이 없습니다. (모두 최신 상태) ###");
			return 0;
		}

		log.info("### 신규 채널 {}개 발견. 유튜브 API 조회 중... ###", newIds.size());

		// 4. 유튜브 API 조회
		var channelDtos = youtubeService.getChannelInfos(newIds);

		// 5. 엔티티 생성 및 저장
		List<Channel> newChannels = channelDtos.stream()
			.map(dto -> {
				String profileUrl = youtubeService.extractBestThumbnailUrl(dto.snippet().thumbnails());

				return Channel.builder()
					.youtubeChannelId(dto.id())
					.channelName(dto.snippet().title())
					.profileImageUrl(profileUrl)
					.uploadPlaylistId(dto.contentDetails().relatedPlaylists().uploads())
					.channelType(idTypeMap.get(dto.id()))
					.build();
			})
			.toList();

		if (!newChannels.isEmpty()) {
			channelRepository.saveAll(newChannels);
			log.info("### 신규 채널 {}개 DB 저장 완료 ###", newChannels.size());
		}

		return newChannels.size();
	}

	/**
	 * [관리자용 2단계] 최근 1주일 영상 데이터 적재
	 * DB에 있는 채널들의 영상을 조회하여 저장합니다.
	 */
	@Transactional
	public void syncLastWeekVideos() {
		List<Channel> channels = channelRepository.findAll();

		if (channels.isEmpty()) {
			log.warn("### 동기화할 채널이 DB에 없습니다. 채널 초기화를 먼저 진행하세요. ###");
			return;
		}

		LocalDateTime oneWeekAgo = LocalDateTime.now(ZONE_KST).minusDays(7);
		log.info("### 비디오 Sync 시작. 기준 시간(KST): {} (대상 채널: {}개) ###", oneWeekAgo, channels.size());

		int totalSaved = 0;

		for (Channel channel : channels) {
			try {
				totalSaved += syncSingleChannelVideos(channel, oneWeekAgo);
			} catch (Exception e) {
				log.error("채널 비디오 동기화 실패: {} ({})", channel.getChannelName(), channel.getYoutubeChannelId(), e);
			}
		}

		log.info("### 비디오 전체 동기화 완료. 총 {}개의 신규 영상 저장됨 ###", totalSaved);
	}

	private int syncSingleChannelVideos(Channel channel, LocalDateTime defaultSince) {
		// 1. DB에서 해당 채널의 가장 최신 영상 날짜를 가져옴
		LocalDateTime lastSavedAt = videoRepository.findLatestPublishedAtByChannel(channel);

		// 2. DB에 데이터가 없으면 기본값(일주일 전) 사용, 있으면 그 시간 이후 데이터만 타겟팅
		// 중복 방지를 위해 마지막 저장 시간보다 1초 뒤부터 조회한다고 가정하거나,
		// API 검색 결과에서 같은 시간대는 ID로 중복 체크
		LocalDateTime searchAfter = (lastSavedAt != null) ? lastSavedAt : defaultSince;

		String videoDurationParam = (channel.getChannelType() == ChannelType.SHORTS) ? "short" : null;

		YoutubeSearchResponse searchResponse = youtubeService.searchLatestVideos(
			channel.getYoutubeChannelId(),
			50,
			videoDurationParam
		);

		if (searchResponse == null || searchResponse.items() == null) {
			return 0;
		}

		// 4. 필터링 및 변환
		List<Video> videosToSave = searchResponse.items().stream()
			.filter(item -> {
				OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
				LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

				return publishedAtKst.isAfter(searchAfter);
			})
			// 혹시 모를 중복(시간이 겹칠 경우 등)을 위해 ID 체크는 안전장치로 유지하되,
			// 위 날짜 필터로 인해 호출 횟수는 확연히 줄어듦
			.filter(item -> !videoRepository.existsByYoutubeVideoId(item.id().videoId()))
			.map(item -> convertToVideoEntity(item, channel))
			.toList();

		// 5. 저장
		if (!videosToSave.isEmpty()) {
			videoRepository.saveAll(videosToSave);
			log.info("[{}] 신규 영상 {}개 저장 (기준: {} 이후)",
				channel.getChannelName(), videosToSave.size(), searchAfter);
			return videosToSave.size();
		}

		return 0;
	}

	private Video convertToVideoEntity(YoutubeSearchResponse.SearchItem item, Channel channel) {
		String videoId = item.id().videoId();
		String title = item.snippet().title();
		String thumbnailUrl = youtubeService.extractBestThumbnailUrl(item.snippet().thumbnails());

		OffsetDateTime odt = OffsetDateTime.parse(item.snippet().publishedAt());
		LocalDateTime publishedAtKst = odt.atZoneSameInstant(ZONE_KST).toLocalDateTime();

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
			.publishedAt(publishedAtKst)
			.build();
	}
}