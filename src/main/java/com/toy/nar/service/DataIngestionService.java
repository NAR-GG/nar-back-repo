package com.toy.nar.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.toy.nar.dto.GameDataCsvDto;
import com.toy.nar.entity.*;
import com.toy.nar.repo.*;
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
	public void ingestCsvData() throws Exception {
		log.info("Starting CSV data ingestion...");
		long startTime = System.currentTimeMillis();

		// 1. ✨ 변동이 적은 마스터 데이터(챔피언, 리그)를 미리 전체 조회하여 캐시를 생성합니다.
		final Map<String, Champion> championCache = championRepository.findAll().stream()
			.collect(Collectors.toMap(
				champion -> NameNormalizer.normalizeChampionName(champion.getChampionNameEn()),
				Function.identity()
			));
		final Map<LeagueIdentifier, League> leagueCache = leagueRepository.findAll().stream()
			.collect(Collectors.toMap(
				league -> new LeagueIdentifier(league.getLeagueName(), league.getSeasonYear(), league.getSeasonSplit(), league.getIsPlayoffs()),
				Function.identity()
			));

		try (Reader reader = new InputStreamReader(new ClassPathResource("lol_esports_data.csv").getInputStream())) {
			CsvToBean<GameDataCsvDto> csvToBean = new CsvToBeanBuilder<GameDataCsvDto>(reader)
				.withType(GameDataCsvDto.class)
				.withIgnoreLeadingWhiteSpace(true).build();

			List<GameDataCsvDto> chunk = new ArrayList<>(CHUNK_SIZE);
			for (GameDataCsvDto dto : csvToBean) {
				chunk.add(dto);
				if (chunk.size() >= CHUNK_SIZE) {
					processChunk(chunk, championCache, leagueCache); // 생성된 캐시를 파라미터로 전달
					chunk.clear();
				}
			}
			if (!chunk.isEmpty()) {
				processChunk(chunk, championCache, leagueCache);
			}
		}
		long endTime = System.currentTimeMillis();
		log.info("✅ Finished CSV data ingestion. Total time: {} ms", (endTime - startTime));
	}

	private void processChunk(List<GameDataCsvDto> chunk, Map<String, Champion> championCache, Map<LeagueIdentifier, League> leagueCache) {
		// 1. 청크에서 필요한 ID 수집
		Set<String> teamNames = new HashSet<>();
		Set<String> playerNames = new HashSet<>();
		Set<String> gameOriginIds = new HashSet<>();

		for (GameDataCsvDto dto : chunk) {
			if (StringUtils.hasText(dto.getTeamname())) teamNames.add(dto.getTeamname());
			if (StringUtils.hasText(dto.getPlayername())) playerNames.add(dto.getPlayername());
			if (StringUtils.hasText(dto.getGameid())) gameOriginIds.add(dto.getGameid());
		}

		// 2. 청크 단위 데이터 조회
		Map<String, Team> teamCache = teamRepository.findAllByNameInIgnoreCase(teamNames).stream().collect(Collectors.toMap(Team::getName, Function.identity(), (e1, e2) -> e1, () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
		Map<String, Player> playerCache = playerRepository.findAllByNameInIgnoreCase(playerNames).stream().collect(Collectors.toMap(Player::getName, Function.identity(), (e1, e2) -> e1, () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

		Set<String> existingGameIds = Collections.emptySet();
		if (!gameOriginIds.isEmpty()) { existingGameIds = gameRepository.findExistingGameIds(gameOriginIds); }

		// 3. 새로운 데이터 저장 및 캐시 업데이트
		saveNewTeams(teamNames, teamCache);
		saveNewPlayers(playerNames, playerCache);
		saveNewLeagues(chunk, leagueCache); // ✨ 새로운 리그가 있다면 저장하고 캐시 업데이트

		// 4. 데이터 조립 (메인 루프 내 DB 접근 없음)
		List<GameParticipant> participantsToSave = new ArrayList<>();
		Map<String, Game> newGameCache = new HashMap<>();

		for (GameDataCsvDto dto : chunk) {
			if (existingGameIds.contains(dto.getGameid())) continue;

			Team team = teamCache.get(dto.getTeamname());
			Player player = playerCache.get(dto.getPlayername());
			if (team == null || player == null) continue;

			String championName = NameNormalizer.normalizeChampionName(dto.getChampion());
			if (!championCache.containsKey(championName)) continue;
			Champion champion = championCache.get(championName);

			// ✨ 캐시에서 리그 정보 조회
			LeagueIdentifier leagueId = new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1);
			League league = leagueCache.get(leagueId);

			if (league == null) {
				log.error("Critical Error: League should have been cached but was not found for leagueId: {}", leagueId);
				continue;
			}

			Game game = newGameCache.computeIfAbsent(dto.getGameid(), gameId -> {
				Game newGame = Game.builder().gameOriginId(gameId).league(league)
					.gameDate(LocalDate.parse(dto.getDate(), CSV_DATE_FORMATTER))
					.gameNumber(dto.getGame()).patch(dto.getPatch()).gameLengthSeconds(dto.getGamelength()).build();
				addBan(newGame, team, championCache, dto.getBan1());
				addBan(newGame, team, championCache, dto.getBan2());
				addBan(newGame, team, championCache, dto.getBan3());
				addBan(newGame, team, championCache, dto.getBan4());
				addBan(newGame, team, championCache, dto.getBan5());
				return newGame;
			});
			participantsToSave.add(GameParticipant.builder().game(game).team(team).player(player).champion(champion).side(dto.getSide()).position(dto.getPosition()).isWin(dto.getResult() == 1).build());
		}

		// 5. 최종 데이터 저장
		if (!newGameCache.values().isEmpty()) {
			gameRepository.saveAll(newGameCache.values());
			gameParticipantRepository.saveAll(participantsToSave);
		}
		log.info("Processed an optimized chunk of {} records.", chunk.size());
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

	// ✨ 새로운 리그를 저장하고 캐시를 업데이트하는 헬퍼 메소드
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
}