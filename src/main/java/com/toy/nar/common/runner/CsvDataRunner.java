package com.toy.nar.common.runner;

import com.toy.nar.game.repository.GameRepository;
import com.toy.nar.common.DataIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvDataRunner implements CommandLineRunner {

	private final DataIngestionService ingestionService;
	private final GameRepository gameRepository;


	@Override
	public void run(String... args) throws Exception {
		if (gameRepository.count() > 0) return;
		else {
			try {
				// 데이터 적재 서비스 호출
				ingestionService.ingestCsvData();
			} catch (Exception e) {
				log.error("❌ Failed to ingest CSV data. The entire transaction has been rolled back.", e);
			}
		}
	}
}