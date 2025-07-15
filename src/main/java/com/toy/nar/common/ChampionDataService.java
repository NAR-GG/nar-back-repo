package com.toy.nar.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.common.dto.ChampionApiResponse;
import com.toy.nar.common.dto.ChampionData;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.repository.ChampionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChampionDataService {

	private final ChampionRepository championRepository;
	private final WebClient webClient; // WebClient 빈을 주입받습니다.

	private static final String VERSION = "15.13.1"; // 최신 버전으로 변경 가능
	private static final String BASE_URL = "https://ddragon.leagueoflegends.com/cdn/" + VERSION;

	@Transactional
	public void fetchAndSaveChampions() {
		// 1. 영어 챔피언 데이터 호출 및 파싱
		ChampionApiResponse enResponse = webClient.get()
			.uri(BASE_URL + "/data/en_US/champion.json")
			.retrieve()
			.bodyToMono(ChampionApiResponse.class)
			.block(); // 동기 방식으로 결과를 기다림

		// 2. 한국어 챔피언 데이터 호출 및 파싱 (이름만 가져오기 위해)
		ChampionApiResponse krResponse = webClient.get()
			.uri(BASE_URL + "/data/ko_KR/champion.json")
			.retrieve()
			.bodyToMono(ChampionApiResponse.class)
			.block();

		if (enResponse == null || krResponse == null) {
			log.error("Failed to fetch champion data from Riot API.");
			return;
		}

		Set<String> existingChampionNames = championRepository.findAll().stream()
			.map(Champion::getChampionNameEn)
			.collect(Collectors.toSet());

		log.info("Fetched {} champions from API. Storing to DB...", enResponse.data().size());
		List<Champion> championsToSave = new ArrayList<>();

		for (ChampionData enChampionData : enResponse.data().values()) {
			String normalizedName = NameNormalizer.normalizeChampionName(enChampionData.id());

			if (existingChampionNames.contains(normalizedName)) {
				continue;
			}

			String championKrName = krResponse.data().get(enChampionData.id()).name();
			String imageUrl = BASE_URL + "/img/champion/" + enChampionData.image().full();

			// 5. Champion 엔티티 생성
			championsToSave.add(Champion.builder()
				.championNameEn(normalizedName)
				.championNameKr(championKrName)
				.imageUrl(imageUrl)
				.build());
		}

		// 6. 모아둔 챔피언 목록을 saveAll로 한번에 저장
		if (!championsToSave.isEmpty()) {
			championRepository.saveAll(championsToSave);
			log.info("Successfully saved {} new champions.", championsToSave.size());
		} else {
			log.info("No new champions to save.");
		}
	}
}
