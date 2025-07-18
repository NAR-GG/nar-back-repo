package com.toy.nar.common.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.toy.nar.participant.repository.ChampionRepository;
import com.toy.nar.common.data.service.ChampionDataService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChampionDataRunner implements CommandLineRunner {

	private final ChampionDataService championDataService;
	private final ChampionRepository championRepository;

	@Override
	public void run(String... args) throws Exception {
		if (championRepository.count() == 0) {
			log.info("'champions' table is empty. Fetching data from Riot API...");
			championDataService.fetchAndSaveChampions();
		} else {
			log.info("'champions' table already has data. Skipping API call.");
		}
	}
}