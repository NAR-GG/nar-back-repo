package com.toy.nar.common.data;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.toy.nar.common.NameNormalizer;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.repository.ChampionRepository;
import com.toy.nar.game.entity.Ban;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.entity.League;
import com.toy.nar.game.repository.GameParticipantRepository;
import com.toy.nar.game.repository.GameRepository;
import com.toy.nar.game.repository.LeagueRepository;
import com.toy.nar.participant.entity.Player;
import com.toy.nar.participant.repository.PlayerRepository;
import com.toy.nar.participant.entity.Team;
import com.toy.nar.participant.repository.TeamRepository;
import com.toy.nar.common.dto.GameDataCsvDto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataIngestionService {

	private final LeagueRepository leagueRepository;
	private final TeamRepository teamRepository;
	private final PlayerRepository playerRepository;
	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final ChampionRepository championRepository;

	private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final int CHUNK_SIZE = 5000;

	// League의 복합 키를 간결하게 표현하기 위한 record (Java 17+ 기능)
	private record LeagueIdentifier(String name, int year, String split, boolean isPlayoffs) {}

	@Transactional
	public DataIngestionResult ingestCsvData() throws Exception {  // ✅ void → DataIngestionResult로 변경
		log.info("Starting CSV data ingestion...");
		long startTime = System.currentTimeMillis();

		// ✅ 결과 추적 객체 생성
		DataIngestionResult result = new DataIngestionResult();

		// 마스터 데이터 캐시 생성
		final Map<String, Champion> championCache = championRepository.findAll().stream()
			.collect(Collectors.toMap(
				champion -> NameNormalizer.normalizeChampionName(champion.getChampionNameEn()),
				Function.identity()
			));
		final Map<LeagueIdentifier, League> leagueCache = leagueRepository.findAll().stream()
			.collect(Collectors.toMap(
				league -> new LeagueIdentifier(league.getLeagueName(), league.getSeasonYear(),
					league.getSeasonSplit(), league.getIsPlayoffs()),
				Function.identity()
			));

		try (Reader reader = new InputStreamReader(new ClassPathResource("lol_esports_data.csv").getInputStream())) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true).build();

			List<GameDataCsvDto> chunk = new ArrayList<>(CHUNK_SIZE);
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				result.incrementProcessedRows();  // ✅ 행 수 카운트

				if (chunk.size() >= CHUNK_SIZE) {
					ChunkProcessingResult chunkResult = processChunk(chunk, championCache, leagueCache);
					result.merge(chunkResult);  // ✅ 청크 결과 합산
					chunk.clear();
				}
			}

			if (!chunk.isEmpty()) {
				ChunkProcessingResult chunkResult = processChunk(chunk, championCache, leagueCache);
				result.merge(chunkResult);  // ✅ 마지막 청크 결과 합산
			}
		}

		long endTime = System.currentTimeMillis();
		log.info("✅ Finished CSV data ingestion. Total time: {} ms", (endTime - startTime));
		log.info("📊 Results: {} rows, {} games processed", result.getProcessedRows(), result.getProcessedGames());

		return result;  // ✅ 결과 반환
	}


	private ChunkProcessingResult processChunk(List<GameDataCsvDto> chunk,
		Map<String, Champion> championCache,
		Map<LeagueIdentifier, League> leagueCache) {
		// ✅ 결과 추적 객체 생성
		ChunkProcessingResult result = new ChunkProcessingResult();

		// 기존 로직들...
		Set<String> teamNames = new HashSet<>();
		Set<String> playerNames = new HashSet<>();
		Set<String> gameOriginIds = new HashSet<>();

		for (GameDataCsvDto dto : chunk) {
			if (StringUtils.hasText(dto.getTeamname())) teamNames.add(dto.getTeamname());
			if (StringUtils.hasText(dto.getPlayername())) playerNames.add(dto.getPlayername());
			if (StringUtils.hasText(dto.getGameid())) gameOriginIds.add(dto.getGameid());
		}

		// 기존 캐시 생성 로직들...
		Map<String, Team> teamCache = teamRepository.findAllByNameInIgnoreCase(teamNames).stream()
			.collect(Collectors.toMap(Team::getName, Function.identity(), (e1, e2) -> e1,
				() -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
		Map<String, Player> playerCache = playerRepository.findAllByNameInIgnoreCase(playerNames).stream()
			.collect(Collectors.toMap(Player::getName, Function.identity(), (e1, e2) -> e1,
				() -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

		Set<String> existingGameIds = Collections.emptySet();
		if (!gameOriginIds.isEmpty()) {
			existingGameIds = gameRepository.findExistingGameIds(gameOriginIds);
			result.skippedGames = existingGameIds.size();  // ✅ 스킵된 게임 수 기록
		}

		// 기존 저장 로직들...
		saveNewTeams(teamNames, teamCache);
		saveNewPlayers(playerNames, playerCache);
		saveNewLeagues(chunk, leagueCache);

		// 게임 처리
		List<GameParticipant> participantsToSave = new ArrayList<>();
		Map<String, Game> newGameCache = new HashMap<>();

		for (GameDataCsvDto dto : chunk) {
			if (existingGameIds.contains(dto.getGameid())) continue;

			// 기존 처리 로직...
			Team team = teamCache.get(dto.getTeamname());
			Player player = playerCache.get(dto.getPlayername());
			if (team == null || player == null) continue;

			String championName = NameNormalizer.normalizeChampionName(dto.getChampion());
			if (!championCache.containsKey(championName)) continue;
			Champion champion = championCache.get(championName);

			LeagueIdentifier leagueId = new LeagueIdentifier(dto.getLeague(), dto.getYear(),
				dto.getSplit(), dto.getPlayoffs() == 1);
			League league = leagueCache.get(leagueId);

			if (league == null) {
				log.error("Critical Error: League should have been cached but was not found for leagueId: {}", leagueId);
				continue;
			}

			Game game = newGameCache.computeIfAbsent(dto.getGameid(), gameId -> {
				Game newGame = Game.builder().gameOriginId(gameId).league(league)
					.gameDate(LocalDate.parse(dto.getDate(), CSV_DATE_FORMATTER))
					.gameNumber(dto.getGame()).patch(dto.getPatch())
					.gameLengthSeconds(dto.getGamelength()).build();
				addBan(newGame, team, championCache, dto.getBan1());
				addBan(newGame, team, championCache, dto.getBan2());
				addBan(newGame, team, championCache, dto.getBan3());
				addBan(newGame, team, championCache, dto.getBan4());
				addBan(newGame, team, championCache, dto.getBan5());
				return newGame;
			});
			participantsToSave.add(GameParticipant.builder().game(game).team(team).player(player)
				.champion(champion).side(dto.getSide()).position(dto.getPosition())
				.isWin(dto.getResult() == 1).build());
		}

		// 저장 및 결과 기록
		if (!newGameCache.values().isEmpty()) {
			gameRepository.saveAll(newGameCache.values());
			gameParticipantRepository.saveAll(participantsToSave);
			result.validGames = newGameCache.size();  // ✅ 성공한 게임 수 기록
		}

		log.info("Processed chunk: {} valid games, {} skipped games",
			result.validGames, result.skippedGames);

		return result;  // ✅ 결과 반환
	}


	// ================== Private Helper Methods ==================

	private void saveNewTeams(Set<String> names, Map<String, Team> cache) {
		List<Team> newTeams = names.stream().filter(name -> !cache.containsKey(name)).map(name -> Team.builder().name(name).build()).collect(Collectors.toList());
		if (!newTeams.isEmpty()) {
			List<Team> savedTeams = teamRepository.saveAll(newTeams);
			savedTeams.forEach(t -> cache.put(t.getName(), t));
		}
	}

	private void saveNewPlayers(Set<String> names, Map<String, Player> cache) {
		List<Player> newPlayers = names.stream().filter(name -> !cache.containsKey(name)).map(name -> Player.builder().name(name).build()).collect(Collectors.toList());
		if (!newPlayers.isEmpty()) {
			List<Player> savedPlayers = playerRepository.saveAll(newPlayers);
			savedPlayers.forEach(p -> cache.put(p.getName(), p));
		}
	}

	private void saveNewLeagues(List<GameDataCsvDto> chunk, Map<LeagueIdentifier, League> leagueCache) {
		Set<LeagueIdentifier> newLeagueIds = chunk.stream()
			.map(dto -> new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1))
			.filter(id -> !leagueCache.containsKey(id))
			.collect(Collectors.toSet());

		if (!newLeagueIds.isEmpty()) {
			List<League> newLeagues = newLeagueIds.stream()
				.map(id -> League.builder().leagueName(id.name).seasonYear(id.year).seasonSplit(id.split).isPlayoffs(id.isPlayoffs).build())
				.collect(Collectors.toList());
			List<League> savedLeagues = leagueRepository.saveAll(newLeagues);
			// 전체 프로세스에서 공유하는 메인 캐시를 업데이트
			savedLeagues.forEach(l -> leagueCache.put(new LeagueIdentifier(l.getLeagueName(), l.getSeasonYear(), l.getSeasonSplit(), l.getIsPlayoffs()), l));
		}
	}

	private void addBan(Game game, Team team, Map<String, Champion> championCache, String banName) {
		if (StringUtils.hasText(banName)) {
			Champion bannedChampion = championCache.get(NameNormalizer.normalizeChampionName(banName));
			if (bannedChampion != null) {
				game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(bannedChampion).build());
			}
		}
	}

	@Getter
	public static class DataIngestionResult {
		private long processedRows = 0;
		private int processedGames = 0;
		private int successfulGames = 0;
		private int failedGames = 0;
		private int skippedGames = 0;
		private int incompleteGames = 0;

		public void incrementProcessedRows() {
			this.processedRows++;
		}

		public void merge(ChunkProcessingResult chunkResult) {
			this.processedGames += chunkResult.validGames;
			this.successfulGames += chunkResult.validGames;
			this.failedGames += chunkResult.failedGames;
			this.skippedGames += chunkResult.skippedGames;
			this.incompleteGames += chunkResult.invalidGames;
		}

	}

	@Getter
	public static class ChunkProcessingResult {
		int validGames     = 0;   // 정상적으로 저장된 게임
		int invalidGames   = 0;   // 10명이 안 되거나 포지션이 잘못돼 스킵한 게임
		int skippedGames   = 0;   // 이미 DB에 있는(complete) 게임
		int failedGames    = 0;   // 처리 중 예외가 난 게임
	}
}