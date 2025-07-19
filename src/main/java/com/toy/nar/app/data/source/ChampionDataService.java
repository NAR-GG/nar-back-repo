package com.toy.nar.app.data.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.common.NameNormalizer;
import com.toy.nar.app.data.source.dto.ChampionApiResponse;
import com.toy.nar.app.data.source.dto.ChampionData;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.repository.ChampionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChampionDataService {

	private final ChampionRepository championRepository;
	private final WebClient webClient; // WebClient 빈을 주입받습니다.

	private static final String VERSION_URL = "https://ddragon.leagueoflegends.com/api/versions.json";
	private static final String BASE_URL = "https://ddragon.leagueoflegends.com/cdn/";

	private String getLatestVersion() {
		try {
			String[] versions = webClient.get()
				.uri(VERSION_URL)
				.retrieve()
				.bodyToMono(String[].class)
				.block();

			if (versions != null && versions.length > 0) {
				String latestVersion = versions[0]; // 첫 번째 항목이 최신 버전
				log.info("🔄 Latest version from Riot API: {}", latestVersion);
				return latestVersion;
			}
		} catch (Exception e) {
			log.error("❌ Failed to fetch latest version from Riot API: {}", e.getMessage());
		}

		// 버전 API 호출 실패 시 fallback 버전 사용
		String fallbackVersion = "15.13.1";
		log.warn("⚠️ Using fallback version: {}", fallbackVersion);
		return fallbackVersion;
	}

	@Transactional
	public void fetchAndSaveChampions() {
		// 1. 최신 버전 가져오기
		String version = getLatestVersion();
		String versionedBaseUrl = BASE_URL + version;

		log.info("🚀 Fetching champion data with version: {}", version);

		// 2. 영어 챔피언 데이터 호출 및 파싱
		ChampionApiResponse enResponse = webClient.get()
			.uri(versionedBaseUrl + "/data/en_US/champion.json")
			.retrieve()
			.bodyToMono(ChampionApiResponse.class)
			.block();

		// 3. 한국어 챔피언 데이터 호출 및 파싱
		ChampionApiResponse krResponse = webClient.get()
			.uri(versionedBaseUrl + "/data/ko_KR/champion.json")
			.retrieve()
			.bodyToMono(ChampionApiResponse.class)
			.block();

		if (enResponse == null || krResponse == null) {
			log.error("❌ Failed to fetch champion data from Riot API.");
			return;
		}

		Set<String> existingChampionNames = championRepository.findAll().stream()
			.map(Champion::getChampionNameEn)
			.collect(Collectors.toSet());

		log.info("📊 Fetched {} champions from API. Checking for updates...", enResponse.data().size());
		List<Champion> championsToSave = new ArrayList<>();
		List<Champion> championsToUpdate = new ArrayList<>();

		for (ChampionData enChampionData : enResponse.data().values()) {
			String normalizedName = NameNormalizer.normalizeChampionName(enChampionData.id());
			String championKrName = krResponse.data().get(enChampionData.id()).name();
			String imageUrl = versionedBaseUrl + "/img/champion/" + enChampionData.image().full();

			if (existingChampionNames.contains(normalizedName)) {
				// 기존 챔피언이 있는 경우 이미지 URL 업데이트 확인
				Champion existingChampion = championRepository.findByChampionNameEn(normalizedName)
					.orElse(null);

				if (existingChampion != null && !imageUrl.equals(existingChampion.getImageUrl())) {
					log.info("🔄 Updating image URL for champion: {} (old: {}, new: {})",
						normalizedName, existingChampion.getImageUrl(), imageUrl);

					existingChampion.updateImageUrl(imageUrl);
					championsToUpdate.add(existingChampion);
				}
			} else {
				// 새로운 챔피언인 경우
				log.info("✨ New champion found: {} ({})", normalizedName, championKrName);

				championsToSave.add(Champion.builder()
					.championNameEn(normalizedName)
					.championNameKr(championKrName)
					.imageUrl(imageUrl)
					.build());
			}
		}

		// 4. 새로운 챔피언 저장
		if (!championsToSave.isEmpty()) {
			championRepository.saveAll(championsToSave);
			log.info("✅ Successfully saved {} new champions.", championsToSave.size());
		}

		// 5. 기존 챔피언 업데이트
		if (!championsToUpdate.isEmpty()) {
			championRepository.saveAll(championsToUpdate);
			log.info("✅ Successfully updated {} champions.", championsToUpdate.size());
		}

		if (championsToSave.isEmpty() && championsToUpdate.isEmpty()) {
			log.info("ℹ️ No new champions to save or update. All data is up to date.");
		}

		log.info("🎉 Champion data sync completed with version: {}", version);
	}
}
