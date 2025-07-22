package com.toy.nar.app.data.ingestion;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.CsvToBeanFilter;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.app.data.ingestion.dto.ChunkProcessingResult;
import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

@Service("dataIngestionFacade") // 이름을 Facade로 명확히 함
@RequiredArgsConstructor
@Slf4j
public class DataIngestionFacade {

	private final GameRepository gameRepository;
	private final EntityResolver entityResolver;
	private final GameProcessor gameProcessor;

	private static final int CHUNK_SIZE = 5000;

	// 역할: League 엔티티를 식별하기 위한 복합 키 record
	public record LeagueIdentifier(String name, int year, String split, boolean isPlayoffs) {
		public static LeagueIdentifier fromDto(GameDataCsvDto dto) {
			return new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1);
		}
		public static LeagueIdentifier fromEntity(League league) {
			return new LeagueIdentifier(league.getLeagueName(), league.getSeasonYear(), league.getSeasonSplit(), league.getIsPlayoffs());
		}
	}

	@Transactional
	public DataIngestionResult ingestCsvData() throws Exception {
		log.info("📥 Starting local CSV data ingestion ('lol_esports_data.csv')...");
		InputStream csvStream = new ClassPathResource("lol_esports_data.csv").getInputStream();
		return ingestFromStream(csvStream);
	}

	@Transactional
	public DataIngestionResult ingestFromStream(InputStream csvStream) throws Exception {
		log.info("📥 Starting stream-based CSV data ingestion with new architecture...");
		long startTime = System.currentTimeMillis();
		DataIngestionResult.Builder resultBuilder = DataIngestionResult.builder();

		// 1. 고정 데이터 캐시 초기화
		entityResolver.initializeCaches();

		try (Reader reader = new InputStreamReader(csvStream)) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true)
				.withFilter(new CsvNonEmptyFilter()) // 유효하지 않은 라인 필터링
				.build();

			List<GameDataCsvDto> chunk = new ArrayList<>(CHUNK_SIZE);
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				resultBuilder.incrementProcessedRows();
				if (chunk.size() >= CHUNK_SIZE) {
					ChunkProcessingResult chunkResult = processChunk(chunk);
					resultBuilder.merge(chunkResult);
					chunk.clear();
				}
			}
			if (!chunk.isEmpty()) {
				ChunkProcessingResult chunkResult = processChunk(chunk);
				resultBuilder.merge(chunkResult);
			}
		}

		DataIngestionResult result = resultBuilder.processingTimeMs(System.currentTimeMillis() - startTime).build();
		log.info("✅ Stream ingestion completed. {}", result.getSummary());
		return result;
	}

	private ChunkProcessingResult processChunk(List<GameDataCsvDto> chunk) {
		int invalidGames = 0;
		int failedGames = 0;

		entityResolver.resolveEntitiesFromChunk(chunk);

		Map<String, List<GameDataCsvDto>> gamesGroupedById = chunk.stream()
			.collect(Collectors.groupingBy(GameDataCsvDto::getGameid));

		Set<String> existingGameIds = gameRepository.findExistingGameIds(gamesGroupedById.keySet());
		int skippedGames = existingGameIds.size();

		List<Game> gamesToSave = new ArrayList<>();

		for (Map.Entry<String, List<GameDataCsvDto>> gameEntry : gamesGroupedById.entrySet()) {
			String gameId = gameEntry.getKey();
			List<GameDataCsvDto> allGameDtos = gameEntry.getValue();

			if (existingGameIds.contains(gameId)) {
				continue;
			}

			List<GameDataCsvDto> playerDtos = allGameDtos.stream()
				.filter(dto -> dto.getPosition() != null &&
					(dto.getPosition().equalsIgnoreCase("top") ||
						dto.getPosition().equalsIgnoreCase("jng") ||
						dto.getPosition().equalsIgnoreCase("mid") ||
						dto.getPosition().equalsIgnoreCase("bot") ||
						dto.getPosition().equalsIgnoreCase("sup")))
				.toList();

			// [변경] 필터링된 플레이어 데이터가 10개인지 검증합니다.
			if (playerDtos.size() != 10) {
				log.warn("Incomplete player data for gameId: {}. Found {} player rows instead of 10. Skipping.", gameId, playerDtos.size());
				invalidGames++;
				continue;
			}

			try {
				Map<String, Game> singleGameCache = new HashMap<>();
				boolean isGameValid = true;

				// [변경] 필터링된 10개의 플레이어 DTO만 처리합니다.
				for (GameDataCsvDto dto : playerDtos) {
					if (gameProcessor.process(dto, singleGameCache).isEmpty()) {
						isGameValid = false;
						break;
					}
				}

				if (isGameValid) {
					gamesToSave.addAll(singleGameCache.values());
				} else {
					log.warn("Game data for gameId: {} is invalid and will be skipped.", gameId);
					invalidGames++;
				}

			} catch (Exception e) {
				log.error("A critical error occurred while processing game {}: {}", gameId, e.getMessage(), e);
				failedGames++;
			}
		}

		if (!gamesToSave.isEmpty()) {
			gameRepository.saveAll(gamesToSave);
		}

		return new ChunkProcessingResult(gamesToSave.size(), invalidGames, skippedGames, failedGames);
	}

	// CSV 파일의 빈 줄이나 필수 값이 없는 줄을 건너뛰기 위한 필터
	private static class CsvNonEmptyFilter implements CsvToBeanFilter {
		@Override
		public boolean allowLine(String[] line) {
			return StringUtils.hasText(line[0]) && StringUtils.hasText(line[1]); // gameid, league 컬럼 확인
		}
	}
}
