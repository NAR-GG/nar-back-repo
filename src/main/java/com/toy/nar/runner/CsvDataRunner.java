package com.toy.nar.runner;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.toy.nar.dto.GameDataCsvDto;
import com.toy.nar.repo.GameRepository;
import com.toy.nar.service.DataIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvDataRunner implements CommandLineRunner {

	private final DataIngestionService ingestionService;
	private final GameRepository gameRepository; // GameRepository 주입
	private static final int CHUNK_SIZE = 1000; // 한 번에 처리할 데이터 묶음 크기

	@Override
	public void run(String... args) throws Exception {
		// Game 테이블에 데이터가 이미 있는지 확인
		if (gameRepository.count() > 0) {
			log.info("'games' table already has data. Skipping CSV data ingestion.");
			return; // 데이터가 있으면 더 이상 진행하지 않고 종료
		}

		log.info("Starting CSV data ingestion...");
		long startTime = System.currentTimeMillis();

		// resources 폴더에 CSV 파일이 있다고 가정
		try (Reader reader = new InputStreamReader(new ClassPathResource("lol_esports_data.csv").getInputStream())) {

			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true) // 필드 앞뒤 공백 무시
				.build();

			List<GameDataCsvDto> chunk = new ArrayList<>();
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				if (chunk.size() >= CHUNK_SIZE) {
					ingestionService.ingestChunk(chunk);
					chunk.clear(); // 청크 처리 후 리스트 비우기
				}
			}

			// 마지막에 남은 데이터 처리
			if (!chunk.isEmpty()) {
				ingestionService.ingestChunk(chunk);
			}
		} catch (Exception e) {
			log.error("Failed to ingest CSV data.", e);
		}

		long endTime = System.currentTimeMillis();
		log.info("✅ Finished CSV data ingestion. Total time: {} ms", (endTime - startTime));
	}
}