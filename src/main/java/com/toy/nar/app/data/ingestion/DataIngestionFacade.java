package com.toy.nar.app.data.ingestion;

import static com.toy.nar.app.data.ingestion.GameProcessor.*;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.CsvToBeanFilter;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.app.data.ingestion.dto.ChunkProcessingResult;
import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;
import com.toy.nar.app.data.maintenance.DeltaCsvFilter;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;
import com.toy.nar.domain.sync.SyncStatus;
import com.toy.nar.domain.sync.SyncStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service("dataIngestionFacade") // 이름을 Facade로 명확히 함
@RequiredArgsConstructor
@Slf4j
public class DataIngestionFacade {

	private final GameRepository gameRepository;
	private final EntityResolver entityResolver;
	private final GameProcessor gameProcessor;
	private final SyncStatusRepository syncStatusRepository;
	private final GameTeamStatRepository gameTeamStatRepository;

	private static final int CHUNK_SIZE = 5000;

	public DataIngestionResult ingestFromStream(InputStream csvStream, String lastProcessedGameId) throws Exception {
		log.info("[Starting] Starting stream-based CSV data ingestion");
		long startTime = System.currentTimeMillis();
		DataIngestionResult.Builder resultBuilder = DataIngestionResult.builder();

		entityResolver.initializeCaches();

		String lastIdInStream = null;

		try (Reader reader = new InputStreamReader(csvStream)) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true)
				.withFilter(new DeltaCsvFilter(lastProcessedGameId))
				.build();

			List<GameDataCsvDto> chunk = new ArrayList<>(CHUNK_SIZE);
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				lastIdInStream = dto.getGameid();
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

		if (StringUtils.hasText(lastIdInStream)) {
			syncStatusRepository.save(new SyncStatus("GOOGLE_DRIVE_CSV", lastIdInStream));
			log.info("Updated last processed gameId to: {}", lastIdInStream);
		}

		DataIngestionResult result = resultBuilder.processingTimeMs(System.currentTimeMillis() - startTime).build();
		log.info("[Completed] Stream ingestion completed. {}", result.getSummary());
		return result;
	}

	@Transactional
	public ChunkProcessingResult processChunk(List<GameDataCsvDto> chunk) {
		int invalidGames = 0;
		int failedGames = 0;

		entityResolver.resolveEntitiesFromChunk(chunk);

		Map<String, List<GameDataCsvDto>> gamesGroupedById = chunk.stream()
			.collect(Collectors.groupingBy(GameDataCsvDto::getGameid));

		Map<String, LocalDateTime> scheduledTimeMap = calculateScheduledTimesForChunk(chunk);
		Set<String> existingGameIds = gameRepository.findExistingGameIds(gamesGroupedById.keySet());
		int skippedGames = existingGameIds.size();

		List<Game> gamesToSave = new ArrayList<>();
		List<GameTeamStat> teamStatsToSave = new ArrayList<>(); // [신규] 팀 통계 저장 리스트

		for (Map.Entry<String, List<GameDataCsvDto>> gameEntry : gamesGroupedById.entrySet()) {
			String gameId = gameEntry.getKey();
			if (existingGameIds.contains(gameId)) {
				continue;
			}

			List<GameDataCsvDto> allGameDtos = gameEntry.getValue();

			// [수정] 플레이어 행과 팀 행을 분리
			Map<Boolean, List<GameDataCsvDto>> partitionedData = allGameDtos.stream()
				.collect(Collectors.partitioningBy(dto ->
					dto.getPosition() != null && !dto.getPosition().isBlank() && !dto.getPosition().equalsIgnoreCase("team")
				));

			List<GameDataCsvDto> playerDtos = partitionedData.get(true);
			List<GameDataCsvDto> teamDtos = partitionedData.get(false);

			// [수정] 데이터 유효성 검증
			if (playerDtos.size() != 10 || teamDtos.size() != 2) {
				log.warn("[Incomplete] Data for gameId: {}. Players: {}, Teams: {}. Skipping.",
					gameId, playerDtos.size(), teamDtos.size());
				invalidGames++;
				continue;
			}

			try {
				Map<String, Game> singleGameCache = new HashMap<>();
				boolean isGameValid = true;

				// 1. 플레이어 데이터 처리
				for (GameDataCsvDto dto : playerDtos) {
					LocalDateTime scheduledTime = scheduledTimeMap.get(dto.getGameid());
					if (gameProcessor.process(dto, singleGameCache, scheduledTime).isEmpty()) {
						isGameValid = false;
						break;
					}
				}

				if (!isGameValid) {
					log.warn("[Skip] Game data for gameId: {} is invalid and will be skipped.", gameId);
					invalidGames++;
					continue;
				}

				// 2. [신규] 팀 데이터 처리
				Game processedGame = singleGameCache.get(gameId);
				if (processedGame == null) {
					log.error("[Error] Game object was not created for gameId: {}. Skipping.", gameId);
					failedGames++;
					continue;
				}

				for (GameDataCsvDto teamDto : teamDtos) {
					gameProcessor.processTeamStats(teamDto, processedGame)
						.ifPresent(teamStatsToSave::add);
				}

				gamesToSave.add(processedGame);

			} catch (Exception e) {
				log.error("[Error] A critical error occurred while processing game {}: {}", gameId, e.getMessage(), e);
				failedGames++;
			}
		}

		// [수정] Cascade 설정에 의해 Game 저장 시 Participant와 PlayerStat이 함께 저장됨
		if (!gamesToSave.isEmpty()) {
			gameRepository.saveAll(gamesToSave);
		}
		// [신규] 팀 통계 데이터 저장
		if (!teamStatsToSave.isEmpty()) {
			gameTeamStatRepository.saveAll(teamStatsToSave);
		}

		return new ChunkProcessingResult(gamesToSave.size(), invalidGames, skippedGames, failedGames);
	}

	private Map<String, LocalDateTime> calculateScheduledTimesForChunk(List<GameDataCsvDto> chunk) {
		Map<String, LocalDateTime> resultMap = new HashMap<>();

		List<GameDataCsvDto> lckGames = chunk.stream()
			.filter(dto -> "LCK".equalsIgnoreCase(dto.getLeague()))
			.toList();

		// 날짜별로 게임들을 그룹화
		Map<LocalDate, List<GameDataCsvDto>> gamesByDate = lckGames.stream()
			.collect(Collectors.groupingBy(dto -> LocalDateTime.parse(dto.getDate(), CSV_DATE_FORMATTER).toLocalDate()));

		for (var entry : gamesByDate.entrySet()) {
			LocalDate date = entry.getKey();
			List<GameDataCsvDto> dailyGames = entry.getValue();

			// 하루의 경기들을 시간순으로 정렬 (1경기, 2경기 순서를 정하기 위해)
			List<String> sortedGameIds = dailyGames.stream()
				.map(dto -> new AbstractMap.SimpleEntry<>(dto.getGameid(), LocalDateTime.parse(dto.getDate(), CSV_DATE_FORMATTER)))
				.distinct() // 게임 ID 중복 제거
				.sorted(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.toList();

			boolean isWeekend = date.getDayOfWeek().getValue() >= 6; // 토, 일

			for (int i = 0; i < sortedGameIds.size(); i++) {
				String gameId = sortedGameIds.get(i);
				int matchOrder = i + 1; // 1번째 경기, 2번째 경기...

				LocalDateTime scheduledTimeKst;
				if (isWeekend) {
					scheduledTimeKst = date.atTime(matchOrder == 1 ? 15 : 17, 0); // 주말: 15시, 17시
				} else {
					scheduledTimeKst = date.atTime(matchOrder == 1 ? 17 : 19, 0); // 평일: 17시, 19시
				}

				LocalDateTime scheduledTimeUtc = scheduledTimeKst
					.atZone(ZoneId.of("Asia/Seoul"))       // 1. KST 시간대 정보 부여
					.withZoneSameInstant(ZoneId.of("UTC")) // 2. 동일한 순간의 UTC 시간으로 변경
					.toLocalDateTime();

				resultMap.put(gameId, scheduledTimeUtc);
			}
		}
		return resultMap;
	}
}
