package com.toy.nar.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.toy.nar.dto.ChampionApiResponse;
import com.toy.nar.dto.ChampionData;
import com.toy.nar.entity.Champion;
import com.toy.nar.repo.ChampionRepository;

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

		log.info("Fetched {} champions from API. Storing to DB...", enResponse.data().size());
		List<Champion> championsToSave = new ArrayList<>();

		// 3. 영어 데이터를 기준으로 반복
		for (ChampionData enChampionData : enResponse.data().values()) {

			// champion_name_en을 기준으로 DB에 이미 있는지 확인
			boolean exists = championRepository.existsByChampionNameEn(enChampionData.id());
			if(exists) {
				continue; // 이미 존재하면 건너뛰기
			}

			// 4. 한국어 이름 매칭 및 이미지 URL 생성
			String championKrName = krResponse.data().get(enChampionData.id()).name();
			String imageUrl = BASE_URL + "/img/champion/" + enChampionData.image().full();

			// 5. Champion 엔티티 생성
			championsToSave.add(Champion.builder()
				.championNameEn(enChampionData.id()) // 고유한 영문 ID
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
