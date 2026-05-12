package com.toy.nar.app.data.ingestion;

import static com.toy.nar.app.data.ingestion.GameProcessor.CSV_DATE_FORMATTER;
import static com.toy.nar.jooq.tables.GamePlayerStat.GAME_PLAYER_STAT;
import static com.toy.nar.jooq.tables.GameTeamStat.GAME_TEAM_STAT;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.toy.nar.app.data.ingestion.dto.ChunkProcessingResult;
import com.toy.nar.app.data.ingestion.dto.DataIngestionResult;
import com.toy.nar.app.data.ingestion.dto.GameDataCsvDto;
import com.toy.nar.app.data.ingestion.dto.GamePlayerStatInsertRow;
import com.toy.nar.app.data.ingestion.dto.GameTeamStatInsertRow;
import com.toy.nar.app.data.ingestion.mapper.GamePlayerStatInsertMapper;
import com.toy.nar.app.data.ingestion.mapper.GameTeamStatInsertMapper;
import com.toy.nar.domain.game.entity.Ban;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.GamePlayerStat;
import com.toy.nar.domain.participant.entity.GameTeamStat;
import com.toy.nar.domain.sync.SyncStatus;
import com.toy.nar.domain.sync.SyncStatusRepository;
import com.toy.nar.jooq.tables.records.GamePlayerStatRecord;
import com.toy.nar.jooq.tables.records.GameTeamStatRecord;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JdbcBatchDataIngestionFacade {

	private static final int ROW_CHUNK_SIZE = 6_000;
	private static final int GAME_BATCH_SIZE = 500;
	private static final int PARTICIPANT_BATCH_SIZE = 1_000;
	private static final int BAN_BATCH_SIZE = 1_000;
	private static final int TEAM_STAT_BATCH_SIZE = 500;
	private static final int PLAYER_STAT_BATCH_SIZE = 300;
	private static final String WRITER_STRATEGY = "jdbc_batch";

	private final GameRepository gameRepository;
	private final EntityResolver entityResolver;
	private final GameProcessor gameProcessor;
	private final SyncStatusRepository syncStatusRepository;
	private final JdbcTemplate jdbcTemplate;
	private final DSLContext dsl;
	private final GamePlayerStatInsertMapper gamePlayerStatInsertMapper;
	private final GameTeamStatInsertMapper gameTeamStatInsertMapper;
	private final MeterRegistry meterRegistry;
	private final TransactionTemplate transactionTemplate;
	private final ReentrantLock ingestionLock = new ReentrantLock();

	public DataIngestionResult ingestFromStream(InputStream csvStream, String lastProcessedGameId) throws Exception {
		if (!ingestionLock.tryLock()) {
			throw new IllegalStateException("Data ingestion is already running.");
		}

		try {
			return doIngestFromStream(csvStream, lastProcessedGameId);
		} finally {
			ingestionLock.unlock();
		}
	}

	private DataIngestionResult doIngestFromStream(InputStream csvStream, String lastProcessedGameId) throws Exception {
		log.info("[Starting] Starting JDBC batch stream-based CSV data ingestion");
		if (StringUtils.hasText(lastProcessedGameId)) {
			log.info("Delta cursor detected ({}), but full-stream scan is used for consistency.", lastProcessedGameId);
		}

		long startTime = System.currentTimeMillis();
		DataIngestionResult.Builder resultBuilder = DataIngestionResult.builder();
		entityResolver.clearCaches();
		entityResolver.initializeCaches();

		String lastIdInStream = null;

		try (Reader reader = new InputStreamReader(csvStream)) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
					.withType(GameDataCsvDto.class)
					.withIgnoreLeadingWhiteSpace(true)
					.build();

			List<GameDataCsvDto> chunk = new ArrayList<>(ROW_CHUNK_SIZE);
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				lastIdInStream = dto.getGameid();
				resultBuilder.incrementProcessedRows();
				flushSafeChunkIfNeeded(chunk, resultBuilder);
			}
			if (!chunk.isEmpty()) {
				resultBuilder.merge(processChunkInTransaction(chunk));
			}
		} finally {
			entityResolver.clearCaches();
		}

		if (StringUtils.hasText(lastIdInStream)) {
			syncStatusRepository.save(new SyncStatus("GOOGLE_DRIVE_CSV", lastIdInStream));
			log.info("Updated last processed gameId to: {}", lastIdInStream);
		}

		long processingTimeMs = System.currentTimeMillis() - startTime;
		DataIngestionResult result = resultBuilder.processingTimeMs(processingTimeMs).build();
		recordIngestionResult(result, processingTimeMs);
		log.info("[Completed] JDBC batch stream ingestion completed. {}", result.getSummary());
		return result;
	}

	private void flushSafeChunkIfNeeded(List<GameDataCsvDto> chunk, DataIngestionResult.Builder resultBuilder) {
		if (chunk.size() < ROW_CHUNK_SIZE) {
			return;
		}

		String tailGameId = chunk.get(chunk.size() - 1).getGameid();
		int splitIndex = chunk.size() - 1;
		while (splitIndex >= 0 && Objects.equals(chunk.get(splitIndex).getGameid(), tailGameId)) {
			splitIndex--;
		}
		splitIndex++;

		if (splitIndex == 0) {
			return;
		}

		List<GameDataCsvDto> toProcess = new ArrayList<>(chunk.subList(0, splitIndex));
		resultBuilder.merge(processChunkInTransaction(toProcess));

		List<GameDataCsvDto> carryOver = new ArrayList<>(chunk.subList(splitIndex, chunk.size()));
		chunk.clear();
		chunk.addAll(carryOver);
	}

	private ChunkProcessingResult processChunkInTransaction(List<GameDataCsvDto> chunk) {
		return transactionTemplate.execute(status -> processChunk(chunk));
	}

	public ChunkProcessingResult processChunk(List<GameDataCsvDto> chunk) {
		int invalidGames = 0;
		int failedGames = 0;

		long resolveStart = System.nanoTime();
		entityResolver.resolveEntitiesFromChunk(chunk);
		long resolveDurationNanos = System.nanoTime() - resolveStart;

		long processStart = System.nanoTime();
		Map<String, List<GameDataCsvDto>> gamesGroupedById = chunk.stream()
				.collect(Collectors.groupingBy(GameDataCsvDto::getGameid, LinkedHashMap::new, Collectors.toList()));

		Map<String, LocalDateTime> scheduledTimeMap = calculateScheduledTimesForChunk(chunk);
		Set<String> existingGameIds = gameRepository.findExistingGameIds(gamesGroupedById.keySet());
		int skippedGames = existingGameIds.size();

		List<Game> gamesToSave = new ArrayList<>();
		List<GameTeamStat> teamStatsToSave = new ArrayList<>();

		for (Map.Entry<String, List<GameDataCsvDto>> gameEntry : gamesGroupedById.entrySet()) {
			String gameId = gameEntry.getKey();
			if (existingGameIds.contains(gameId)) {
				continue;
			}

			List<GameDataCsvDto> allGameDtos = gameEntry.getValue();
			Map<Boolean, List<GameDataCsvDto>> partitionedData = allGameDtos.stream()
					.collect(Collectors.partitioningBy(dto ->
							dto.getPosition() != null && !dto.getPosition().isBlank()
									&& !dto.getPosition().equalsIgnoreCase("team")));

			List<GameDataCsvDto> playerDtos = partitionedData.get(true);
			List<GameDataCsvDto> teamDtos = partitionedData.get(false);

			if (playerDtos.size() != 10 || teamDtos.size() != 2) {
				log.warn("[Incomplete] Data for gameId: {}. Players: {}, Teams: {}. Skipping.",
						gameId, playerDtos.size(), teamDtos.size());
				invalidGames++;
				continue;
			}

			try {
				Map<String, Game> singleGameCache = new HashMap<>();
				boolean isGameValid = true;

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

				Game processedGame = singleGameCache.get(gameId);
				if (processedGame == null) {
					log.error("[Error] Game object was not created for gameId: {}. Skipping.", gameId);
					failedGames++;
					continue;
				}

				java.util.Set<Long> distinctPlayerIds = processedGame.getParticipants().stream()
					.map(p -> p.getPlayer().getId())
					.collect(java.util.stream.Collectors.toSet());
				if (distinctPlayerIds.size() != processedGame.getParticipants().size()) {
					log.warn("[Skip] gameId={} 동일 player_id 중복 (NameNormalizer 정규화 충돌). 스킵.", gameId);
					invalidGames++;
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
		long processDurationNanos = System.nanoTime() - processStart;

		long writeStart = System.nanoTime();
		WriteCounts writeCounts = writeJdbcBatch(gamesToSave, teamStatsToSave);
		long writeDurationNanos = System.nanoTime() - writeStart;

		recordChunkMetrics(
				chunk.size(),
				writeCounts,
				invalidGames,
				skippedGames,
				failedGames,
				resolveDurationNanos,
				processDurationNanos,
				writeDurationNanos);

		return new ChunkProcessingResult(gamesToSave.size(), invalidGames, skippedGames, failedGames);
	}

	private WriteCounts writeJdbcBatch(List<Game> games, List<GameTeamStat> teamStats) {
		if (games.isEmpty()) {
			return new WriteCounts(0, 0, 0, 0, 0);
		}

		insertGames(games);
		Map<String, Long> gameIdByOriginId = findGameIds(games);

		List<GameParticipant> participants = games.stream()
				.flatMap(game -> game.getParticipants().stream())
				.toList();
		List<Ban> bans = games.stream()
				.flatMap(game -> game.getBans().stream())
				.toList();

		insertParticipants(participants, gameIdByOriginId);
		Map<ParticipantKey, Long> participantIdByKey = findParticipantIds(games, gameIdByOriginId);

		insertPlayerStats(participants, gameIdByOriginId, participantIdByKey);
		insertBans(bans, gameIdByOriginId);
		insertTeamStats(teamStats, gameIdByOriginId);

		return new WriteCounts(games.size(), participants.size(), participants.size(), bans.size(), teamStats.size());
	}

	private void insertGames(List<Game> games) {
		String sql = """
				INSERT INTO games (
					game_origin_id, league_id, actual_game_start_time, scheduled_game_start_time,
					game_number, patch, game_length_seconds, ckpm
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""";

		batchUpdate(sql, games, GAME_BATCH_SIZE, (ps, game) -> {
			set(ps, 1, game.getGameOriginId());
			set(ps, 2, game.getLeague().getId());
			set(ps, 3, game.getActualGameStartTime());
			set(ps, 4, game.getScheduledGameStartTime());
			set(ps, 5, game.getGameNumber());
			set(ps, 6, game.getPatch());
			set(ps, 7, game.getGameLengthSeconds());
			set(ps, 8, game.getCkpm());
		});
	}

	private Map<String, Long> findGameIds(List<Game> games) {
		List<String> originIds = games.stream().map(Game::getGameOriginId).toList();
		String inClause = placeholders(originIds.size());
		String sql = "SELECT game_id, game_origin_id FROM games WHERE game_origin_id IN (" + inClause + ")";

		Map<String, Long> result = new HashMap<>();
		jdbcTemplate.query(sql, ps -> {
			for (int i = 0; i < originIds.size(); i++) {
				ps.setString(i + 1, originIds.get(i));
			}
		}, rs -> {
			result.put(rs.getString("game_origin_id"), rs.getLong("game_id"));
		});
		return result;
	}

	private void insertParticipants(List<GameParticipant> participants, Map<String, Long> gameIdByOriginId) {
		String sql = """
				INSERT INTO game_participants (
					game_id, player_id, team_id, side, position, champion_id, is_win
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""";

		batchUpdate(sql, participants, PARTICIPANT_BATCH_SIZE, (ps, participant) -> {
			set(ps, 1, gameIdByOriginId.get(participant.getGame().getGameOriginId()));
			set(ps, 2, participant.getPlayer().getId());
			set(ps, 3, participant.getTeam().getId());
			set(ps, 4, participant.getSide());
			set(ps, 5, participant.getPosition());
			set(ps, 6, participant.getChampion().getId());
			set(ps, 7, participant.getIsWin());
		});
	}

	private Map<ParticipantKey, Long> findParticipantIds(List<Game> games, Map<String, Long> gameIdByOriginId) {
		List<Long> gameIds = games.stream()
				.map(game -> gameIdByOriginId.get(game.getGameOriginId()))
				.filter(Objects::nonNull)
				.toList();

		String sql = """
				SELECT participant_game_id, game_id, player_id
				FROM game_participants
				WHERE game_id IN (%s)
				""".formatted(placeholders(gameIds.size()));

		Map<ParticipantKey, Long> result = new HashMap<>();
		jdbcTemplate.query(sql, ps -> {
			for (int i = 0; i < gameIds.size(); i++) {
				ps.setLong(i + 1, gameIds.get(i));
			}
		}, rs -> {
			result.put(
					new ParticipantKey(rs.getLong("game_id"), rs.getLong("player_id")),
					rs.getLong("participant_game_id"));
		});
		return result;
	}

	private void insertBans(List<Ban> bans, Map<String, Long> gameIdByOriginId) {
		if (bans.isEmpty()) {
			return;
		}

		String sql = """
				INSERT INTO bans (game_id, team_id, banned_champion_id)
				VALUES (?, ?, ?)
				""";

		batchUpdate(sql, bans, BAN_BATCH_SIZE, (ps, ban) -> {
			set(ps, 1, gameIdByOriginId.get(ban.getGame().getGameOriginId()));
			set(ps, 2, ban.getTeam().getId());
			set(ps, 3, ban.getBannedChampion().getId());
		});
	}

	private void insertTeamStats(List<GameTeamStat> teamStats, Map<String, Long> gameIdByOriginId) {
		if (teamStats.isEmpty()) {
			return;
		}

		List<GameTeamStatInsertRow> rows = teamStats.stream()
				.map(stat -> gameTeamStatInsertMapper.toRow(
						gameIdByOriginId.get(stat.getGame().getGameOriginId()),
						stat.getTeam().getId(),
						stat))
				.toList();

		batchInsertTeamStats(rows);
	}

	private void insertPlayerStats(
			List<GameParticipant> participants,
			Map<String, Long> gameIdByOriginId,
			Map<ParticipantKey, Long> participantIdByKey) {
		if (participants.isEmpty()) {
			return;
		}

		List<GamePlayerStatInsertRow> rows = participants.stream()
				.map(participant -> {
					Long gameId = gameIdByOriginId.get(participant.getGame().getGameOriginId());
					Long participantId = participantIdByKey.get(new ParticipantKey(gameId, participant.getPlayer().getId()));
					if (participantId == null) {
						throw new IllegalStateException("Participant id was not resolved. gameOriginId="
								+ participant.getGame().getGameOriginId()
								+ ", playerId=" + participant.getPlayer().getId());
					}
					GamePlayerStat stat = participant.getStat();
					return gamePlayerStatInsertMapper.toRow(participantId, stat);
				})
				.toList();

		batchInsertPlayerStats(rows);
	}

	private void batchInsertPlayerStats(List<GamePlayerStatInsertRow> rows) {
		for (int start = 0; start < rows.size(); start += PLAYER_STAT_BATCH_SIZE) {
			List<GamePlayerStatRecord> records = rows.subList(start, Math.min(start + PLAYER_STAT_BATCH_SIZE, rows.size()))
					.stream()
					.map(row -> dsl.newRecord(GAME_PLAYER_STAT, row))
					.toList();
			dsl.batchInsert(records).execute();
		}
	}

	private void batchInsertTeamStats(List<GameTeamStatInsertRow> rows) {
		for (int start = 0; start < rows.size(); start += TEAM_STAT_BATCH_SIZE) {
			List<GameTeamStatRecord> records = rows.subList(start, Math.min(start + TEAM_STAT_BATCH_SIZE, rows.size()))
					.stream()
					.map(row -> dsl.newRecord(GAME_TEAM_STAT, row))
					.toList();
			dsl.batchInsert(records).execute();
		}
	}

	private <T> void batchUpdate(
			String sql,
			List<T> rows,
			int batchSize,
			ThrowingPreparedStatementSetter<T> setter) {
		for (int start = 0; start < rows.size(); start += batchSize) {
			List<T> slice = rows.subList(start, Math.min(start + batchSize, rows.size()));
			jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					setter.setValues(ps, slice.get(i));
				}

				@Override
				public int getBatchSize() {
					return slice.size();
				}
			});
		}
	}

	private void recordIngestionResult(DataIngestionResult result, long processingTimeMs) {
		Timer.builder("nar.data.ingestion.duration")
				.description("Total CSV ingestion duration")
				.tag("writer", WRITER_STRATEGY)
				.register(meterRegistry)
				.record(processingTimeMs, TimeUnit.MILLISECONDS);

		meterRegistry.counter("nar.data.ingestion.rows.total", "writer", WRITER_STRATEGY)
				.increment(result.processedRows());
	}

	private void recordChunkMetrics(
			int chunkRows,
			WriteCounts writeCounts,
			int invalidGames,
			int skippedGames,
			int failedGames,
			long resolveDurationNanos,
			long processDurationNanos,
			long writeDurationNanos) {
		recordChunkPhase("resolve", resolveDurationNanos);
		recordChunkPhase("process", processDurationNanos);
		recordChunkPhase("write", writeDurationNanos);

		meterRegistry.summary("nar.data.ingestion.chunk.rows", "writer", WRITER_STRATEGY)
				.record(chunkRows);
		recordWriteRows("game", writeCounts.games());
		recordWriteRows("game_participant", writeCounts.participants());
		recordWriteRows("game_player_stat", writeCounts.playerStats());
		recordWriteRows("ban", writeCounts.bans());
		recordWriteRows("game_team_stat", writeCounts.teamStats());

		meterRegistry.counter("nar.data.ingestion.games.total", "writer", WRITER_STRATEGY, "result", "success")
				.increment(writeCounts.games());
		meterRegistry.counter("nar.data.ingestion.games.total", "writer", WRITER_STRATEGY, "result", "invalid")
				.increment(invalidGames);
		meterRegistry.counter("nar.data.ingestion.games.total", "writer", WRITER_STRATEGY, "result", "skipped")
				.increment(skippedGames);
		meterRegistry.counter("nar.data.ingestion.games.total", "writer", WRITER_STRATEGY, "result", "failed")
				.increment(failedGames);
	}

	private void recordWriteRows(String table, int rows) {
		meterRegistry.summary("nar.data.ingestion.write.rows", "writer", WRITER_STRATEGY, "table", table)
				.record(rows);
	}

	private void recordChunkPhase(String phase, long durationNanos) {
		Timer.builder("nar.data.ingestion.chunk.phase.duration")
				.description("CSV ingestion chunk phase duration")
				.tag("writer", WRITER_STRATEGY)
				.tag("phase", phase)
				.register(meterRegistry)
				.record(durationNanos, TimeUnit.NANOSECONDS);
	}

	private Map<String, LocalDateTime> calculateScheduledTimesForChunk(List<GameDataCsvDto> chunk) {
		Map<String, LocalDateTime> resultMap = new HashMap<>();
		List<GameDataCsvDto> lckGames = chunk.stream()
				.filter(dto -> "LCK".equalsIgnoreCase(dto.getLeague()))
				.toList();

		Map<LocalDate, List<GameDataCsvDto>> gamesByDate = lckGames.stream()
				.collect(Collectors.groupingBy(dto -> LocalDateTime.parse(dto.getDate(), CSV_DATE_FORMATTER).toLocalDate()));

		for (var entry : gamesByDate.entrySet()) {
			LocalDate date = entry.getKey();
			List<GameDataCsvDto> dailyGames = entry.getValue();

			List<String> sortedGameIds = dailyGames.stream()
					.map(dto -> new AbstractMap.SimpleEntry<>(
							dto.getGameid(),
							LocalDateTime.parse(dto.getDate(), CSV_DATE_FORMATTER)))
					.distinct()
					.sorted(Map.Entry.comparingByValue())
					.map(Map.Entry::getKey)
					.toList();

			boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
			for (int i = 0; i < sortedGameIds.size(); i++) {
				String gameId = sortedGameIds.get(i);
				int matchOrder = i + 1;
				LocalDateTime scheduledTimeKst = date.atTime(
						isWeekend ? (matchOrder == 1 ? 15 : 17) : (matchOrder == 1 ? 17 : 19),
						0);
				LocalDateTime scheduledTimeUtc = scheduledTimeKst
						.atZone(ZoneId.of("Asia/Seoul"))
						.withZoneSameInstant(ZoneId.of("UTC"))
						.toLocalDateTime();
				resultMap.put(gameId, scheduledTimeUtc);
			}
		}

		return resultMap;
	}

	private String placeholders(int size) {
		return String.join(",", Collections.nCopies(size, "?"));
	}

	private void set(PreparedStatement ps, int index, Object value) throws SQLException {
		ps.setObject(index, value);
	}

	@FunctionalInterface
	private interface ThrowingPreparedStatementSetter<T> {
		void setValues(PreparedStatement ps, T row) throws SQLException;
	}

	private record ParticipantKey(Long gameId, Long playerId) {
	}

	private record WriteCounts(int games, int participants, int playerStats, int bans, int teamStats) {
	}
}
