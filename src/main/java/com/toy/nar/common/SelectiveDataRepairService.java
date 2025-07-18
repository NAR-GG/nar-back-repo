package com.toy.nar.common;

import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.toy.nar.common.dto.GameDataCsvDto;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.entity.League;
import com.toy.nar.game.repository.GameParticipantRepository;
import com.toy.nar.game.repository.GameRepository;
import com.toy.nar.game.repository.LeagueRepository;
import com.toy.nar.participant.entity.Champion;
import com.toy.nar.participant.entity.Player;
import com.toy.nar.participant.entity.Team;
import com.toy.nar.participant.repository.ChampionRepository;
import com.toy.nar.participant.repository.PlayerRepository;
import com.toy.nar.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelectiveDataRepairService {

	private final GameStatusAnalyzer gameAnalyzer;
	private final GameParticipantRepository gameParticipantRepository;
	private final GameRepository gameRepository;
	private final TeamRepository teamRepository;
	private final PlayerRepository playerRepository;
	private final ChampionRepository championRepository;
	private final LeagueRepository leagueRepository;
	private final GameIntegrityValidator gameValidator;

	private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private record LeagueIdentifier(String name, int year, String split, boolean isPlayoffs) {}

	@Transactional
	public RepairResult repairIncompleteGamesFromCsv() throws Exception {
		log.info("🔧 Starting selective repair of incomplete games...");
		long startTime = System.currentTimeMillis();

		// 1. 현재 DB 상태 분석
		GameStatusAnalyzer.GameStatusReport statusReport = gameAnalyzer.analyzeGameStatus();

		if (statusReport.getIncompleteGames() == 0) {
			log.info("✅ No incomplete games found. Nothing to repair.");
			return RepairResult.noRepairNeeded();
		}

		RepairResult result = new RepairResult();
		result.setInitialIncompleteGames((int) statusReport.getIncompleteGames());

		// 2. 불완전한 게임의 gameOriginId 조회
		Set<String> incompleteGameOriginIds = getIncompleteGameOriginIds(statusReport.getIncompleteGameIds());
		log.info("🎯 Target games for repair: {}", incompleteGameOriginIds.size());

		// 3. 마스터 데이터 캐시
		Map<String, Champion> championCache = loadChampionCache();
		Map<LeagueIdentifier, League> leagueCache = loadLeagueCache();

		// 4. CSV 파일에서 대상 게임만 추출하여 수정
		result = processTargetGamesFromCsv(incompleteGameOriginIds, championCache, leagueCache, result);

		long endTime = System.currentTimeMillis();
		result.setProcessingTime(endTime - startTime);

		logRepairResults(result);
		return result;
	}

	/**
	 * 불완전한 게임들의 gameOriginId를 조회합니다.
	 */
	private Set<String> getIncompleteGameOriginIds(Set<Long> incompleteGameIds) {
		if (incompleteGameIds.isEmpty()) {
			return Collections.emptySet();
		}

		return gameRepository.findGameOriginIdsByIds(incompleteGameIds);
	}

	/**
	 * CSV에서 대상 게임만 처리합니다.
	 */
	private RepairResult processTargetGamesFromCsv(Set<String> targetGameIds,
		Map<String, Champion> championCache,
		Map<LeagueIdentifier, League> leagueCache,
		RepairResult result) throws Exception {

		try (Reader reader = new InputStreamReader(new ClassPathResource("lol_esports_data.csv").getInputStream())) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true).build();

			// 🔥 대상 게임들만 수집
			Map<String, List<GameDataCsvDto>> targetGames = new LinkedHashMap<>();

			for (GameDataCsvDto dto : csvToBean) {
				if (targetGameIds.contains(dto.getGameid())) {
					targetGames.computeIfAbsent(dto.getGameid(), k -> new ArrayList<>()).add(dto);
				}
			}

			log.info("📥 Found {} target games in CSV", targetGames.size());

			// 🔥 게임별로 처리
			for (Map.Entry<String, List<GameDataCsvDto>> entry : targetGames.entrySet()) {
				String gameOriginId = entry.getKey();
				List<GameDataCsvDto> gameData = entry.getValue();

				GameRepairResult repairResult = repairSingleGame(gameOriginId, gameData, championCache, leagueCache);
				result.merge(repairResult);
			}
		}

		return result;
	}

	/**
	 * 단일 게임을 수정합니다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public GameRepairResult repairSingleGame(String gameOriginId,
		List<GameDataCsvDto> csvGameData,
		Map<String, Champion> championCache,
		Map<LeagueIdentifier, League> leagueCache) {

		log.debug("🔧 Repairing game: {}", gameOriginId);

		// 1. 게임 검증
		GameIntegrityValidator.GameValidationResult validation = gameValidator.validateGame(gameOriginId, csvGameData);

		if (!validation.isValid()) {
			log.warn("❌ Cannot repair game {}: {}", gameOriginId, validation.getErrorMessage());
			return GameRepairResult.failed(gameOriginId, validation.getErrorMessage());
		}

		try {
			// 2. 기존 불완전한 데이터 삭제
			Optional<Game> existingGame = gameRepository.findByGameOriginId(gameOriginId);
			if (existingGame.isPresent()) {
				Long gameId = existingGame.get().getId();

				log.debug("🗑️ Removing incomplete data for game: {}", gameOriginId);
				gameParticipantRepository.deleteByGameId(gameId);
				gameRepository.deleteById(gameId);
			}

			// 3. 완전한 새 데이터 생성
			List<GameDataCsvDto> validPlayerData = validation.getValidPlayerData();
			createCompleteGame(gameOriginId, validPlayerData, championCache, leagueCache);

			log.debug("✅ Successfully repaired game: {}", gameOriginId);
			return GameRepairResult.success(gameOriginId);

		} catch (Exception e) {
			log.error("❌ Failed to repair game: {}", gameOriginId, e);
			return GameRepairResult.failed(gameOriginId, e.getMessage());
		}
	}

	/**
	 * 완전한 게임을 생성합니다.
	 */
	private void createCompleteGame(String gameOriginId,
		List<GameDataCsvDto> playerData,
		Map<String, Champion> championCache,
		Map<LeagueIdentifier, League> leagueCache) {

		// 팀/플레이어 캐시 생성
		Set<String> teamNames = playerData.stream()
			.map(GameDataCsvDto::getTeamname)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());
		Set<String> playerNames = playerData.stream()
			.map(GameDataCsvDto::getPlayername)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());

		Map<String, Team> teamCache = createTeamCache(teamNames);
		Map<String, Player> playerCache = createPlayerCache(playerNames);

		// 새로운 팀/플레이어 저장
		saveNewTeams(teamNames, teamCache);
		saveNewPlayers(playerNames, playerCache);
		saveNewLeagues(playerData, leagueCache);

		// 게임 생성
		GameDataCsvDto firstRecord = playerData.get(0);
		Game game = createGame(firstRecord, leagueCache);
		game = gameRepository.save(game);

		// 게임 참가자들 생성
		List<GameParticipant> participants = new ArrayList<>();
		for (GameDataCsvDto dto : playerData) {
			GameParticipant participant = createGameParticipant(dto, game, teamCache, playerCache, championCache);
			if (participant != null) {
				participants.add(participant);
			}
		}

		gameParticipantRepository.saveAll(participants);
		log.debug("💾 Created complete game with {} participants", participants.size());
	}

	// 헬퍼 메서드들
	private Map<String, Champion> loadChampionCache() {
		return championRepository.findAll().stream()
			.collect(Collectors.toMap(
				champion -> NameNormalizer.normalizeChampionName(champion.getChampionNameEn()),
				Function.identity()
			));
	}

	private Map<LeagueIdentifier, League> loadLeagueCache() {
		return leagueRepository.findAll().stream()
			.collect(Collectors.toMap(
				league -> new LeagueIdentifier(league.getLeagueName(),
					league.getSeasonYear(), league.getSeasonSplit(), league.getIsPlayoffs()),
				Function.identity()
			));
	}

	private Map<String, Team> createTeamCache(Set<String> teamNames) {
		return teamRepository.findAllByNameInIgnoreCase(teamNames).stream()
			.collect(Collectors.toMap(Team::getName, Function.identity(),
				(e1, e2) -> e1, () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
	}

	private Map<String, Player> createPlayerCache(Set<String> playerNames) {
		return playerRepository.findAllByNameInIgnoreCase(playerNames).stream()
			.collect(Collectors.toMap(Player::getName, Function.identity(),
				(e1, e2) -> e1, () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
	}

	private void saveNewTeams(Set<String> names, Map<String, Team> cache) {
		List<Team> newTeams = names.stream()
			.filter(name -> !cache.containsKey(name))
			.map(name -> Team.builder().name(name).build())
			.collect(Collectors.toList());

		if (!newTeams.isEmpty()) {
			List<Team> savedTeams = teamRepository.saveAll(newTeams);
			savedTeams.forEach(t -> cache.put(t.getName(), t));
		}
	}

	private void saveNewPlayers(Set<String> names, Map<String, Player> cache) {
		List<Player> newPlayers = names.stream()
			.filter(name -> !cache.containsKey(name))
			.map(name -> Player.builder().name(name).build())
			.collect(Collectors.toList());

		if (!newPlayers.isEmpty()) {
			List<Player> savedPlayers = playerRepository.saveAll(newPlayers);
			savedPlayers.forEach(p -> cache.put(p.getName(), p));
		}
	}

	private void saveNewLeagues(List<GameDataCsvDto> playerData, Map<LeagueIdentifier, League> leagueCache) {
		Set<LeagueIdentifier> newLeagueIds = playerData.stream()
			.map(dto -> new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1))
			.filter(id -> !leagueCache.containsKey(id))
			.collect(Collectors.toSet());

		if (!newLeagueIds.isEmpty()) {
			List<League> newLeagues = newLeagueIds.stream()
				.map(id -> League.builder().leagueName(id.name).seasonYear(id.year).seasonSplit(id.split).isPlayoffs(id.isPlayoffs).build())
				.collect(Collectors.toList());
			List<League> savedLeagues = leagueRepository.saveAll(newLeagues);
			savedLeagues.forEach(l -> leagueCache.put(new LeagueIdentifier(l.getLeagueName(), l.getSeasonYear(), l.getSeasonSplit(), l.getIsPlayoffs()), l));
		}
	}

	private Game createGame(GameDataCsvDto dto, Map<LeagueIdentifier, League> leagueCache) {
		LeagueIdentifier leagueId = new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1);
		League league = leagueCache.get(leagueId);

		if (league == null) {
			throw new IllegalArgumentException("League not found: " + leagueId);
		}

		return Game.builder()
			.gameOriginId(dto.getGameid())
			.league(league)
			.gameDate(LocalDate.parse(dto.getDate(), CSV_DATE_FORMATTER))
			.gameNumber(dto.getGame())
			.patch(dto.getPatch())
			.gameLengthSeconds(dto.getGamelength())
			.build();
	}

	private GameParticipant createGameParticipant(GameDataCsvDto dto, Game game,
		Map<String, Team> teamCache,
		Map<String, Player> playerCache,
		Map<String, Champion> championCache) {

		Team team = teamCache.get(dto.getTeamname());
		Player player = playerCache.get(dto.getPlayername());

		if (team == null || player == null) {
			log.warn("Missing team or player for game {}: team={}, player={}",
				dto.getGameid(), dto.getTeamname(), dto.getPlayername());
			return null;
		}

		String championName = NameNormalizer.normalizeChampionName(dto.getChampion());
		Champion champion = championCache.get(championName);

		if (champion == null) {
			log.warn("Missing champion for game {}: {}", dto.getGameid(), championName);
			return null;
		}

		return GameParticipant.builder()
			.game(game)
			.team(team)
			.player(player)
			.champion(champion)
			.side(dto.getSide())
			.position(dto.getPosition())
			.isWin(dto.getResult() == 1)
			.build();
	}

	private void logRepairResults(RepairResult result) {
		log.info("🔧 Repair completed:");
		log.info("   ⏱️ Processing time: {} ms", result.getProcessingTime());
		log.info("   🎯 Initial incomplete games: {}", result.getInitialIncompleteGames());
		log.info("   ✅ Successfully repaired: {}", result.getRepairedGames());
		log.info("   ❌ Failed to repair: {}", result.getFailedGames());
		log.info("   ⏭️ Games not found in CSV: {}", result.getNotFoundGames());
	}
}

