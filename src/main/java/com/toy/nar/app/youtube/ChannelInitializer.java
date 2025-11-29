package com.toy.nar.app.youtube;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.toy.nar.common.util.YoutubeProperties;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.repository.ChannelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class ChannelInitializer implements CommandLineRunner {

	private final YoutubeService youtubeService;
	private final ChannelRepository channelRepository;
	private final YoutubeProperties youtubeProperties;

	@Override
	public void run(String... args) throws Exception {
		log.info("### 채널 초기화 데이터 점검 시작 ###");

		// 1. yml 데이터 준비
		Map<String, ChannelType> idTypeMap = youtubeProperties.seedChannels().entrySet().stream()
			.flatMap(entry -> entry.getValue().stream()
				.map(id -> Map.entry(id, entry.getKey())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		List<String> allTargetIds = new ArrayList<>(idTypeMap.keySet());

		if (allTargetIds.isEmpty()) return;

		Set<String> existingYoutubeIds = channelRepository.findByYoutubeChannelIdIn(allTargetIds).stream()
			.map(Channel::getYoutubeChannelId) // PK(id)가 아니라 youtubeChannelId를 비교
			.collect(Collectors.toSet());

		// 3. 없는 ID 필터링
		List<String> newIds = allTargetIds.stream()
			.filter(id -> !existingYoutubeIds.contains(id))
			.toList();

		if (newIds.isEmpty()) {
			log.info("### 모든 채널이 이미 DB에 존재합니다. 건너뜀 ###");
			return;
		}

		log.info("### 신규 채널 {}개 발견. 조회 중... ###", newIds.size());

		// 4. API 조회 (Service는 DTO 리스트 반환)
		var channelDtos = youtubeService.getChannelInfos(newIds);

		// 5. 엔티티 생성
		List<Channel> newChannels = channelDtos.stream()
			.map(dto -> Channel.builder()
				.youtubeChannelId(dto.id())
				.channelName(dto.snippet().title())
				.uploadPlaylistId(dto.contentDetails().relatedPlaylists().uploads())
				.channelType(idTypeMap.get(dto.id()))
				.build()
			)
			.toList();

		if (!newChannels.isEmpty()) {
			channelRepository.saveAll(newChannels);
			log.info("### 신규 채널 {}개 저장 완료 ###", newChannels.size());
		}
	}
}