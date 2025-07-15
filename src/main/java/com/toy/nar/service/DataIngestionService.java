package com.toy.nar.service;

import com.toy.nar.dto.GameDataCsvDto;
import com.toy.nar.entity.*;
import com.toy.nar.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
	private final BanRepository banRepository;
	private final ChampionRepository championRepository;

	private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


	@Transactional
	public void ingestChunk(List<GameDataCsvDto> chunk) {
		Map<String, Game> gameCache = new HashMap<>();
		Map<String, Team> teamCache = new HashMap<>();
		Map<String, Player> playerCache = new HashMap<>();
		Map<String, Champion> championCache = new HashMap<>();

		List<GameParticipant> participantsToSave = new ArrayList<>();

		for (GameDataCsvDto dto : chunk) {

			String normalizedChampionPickName = normalizeChampionName(dto.getChampion());
			League league = findOrCreateLeague(dto);

			Team team = teamCache.computeIfAbsent(dto.getTeamname(), t -> findOrCreateTeam(dto));
			Player player = playerCache.computeIfAbsent(dto.getPlayername(), p -> findOrCreatePlayer(dto));

			if (!StringUtils.hasText(dto.getChampion())) {
				log.warn("Skipping row due to missing champion name. GameID: {}", dto.getGameid());
				continue; // 현재 DTO 처리를 건너뛰고 다음 DTO로 넘어갑니다.
			}

			Champion champion = championCache.computeIfAbsent(normalizedChampionPickName, this::findOrCreateChampion);

			// findOrCreateChampion 내부 로직에 의해 null이 반환될 수도 있으므로 한번 더 체크
			if (champion == null) {
				log.warn("Skipping row because champion '{}' could not be found. GameID: {}", dto.getChampion(), dto.getGameid());
				continue;
			}


			Game game = gameCache.computeIfAbsent(dto.getGameid(), gid -> findOrCreateGame(dto, league));

			participantsToSave.add(GameParticipant.builder()
				.game(game).team(team).player(player).champion(champion)
				.side(dto.getSide()).position(dto.getPosition())
				.isWin(dto.getResult() == 1).build());

			if (game.getBans().isEmpty() && !banRepository.existsByGame(game)) {
				if (StringUtils.hasText(dto.getBan1())) game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(findOrCreateChampion(normalizeChampionName(dto.getBan1()))).build());
				if (StringUtils.hasText(dto.getBan2())) game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(findOrCreateChampion(normalizeChampionName(dto.getBan2()))).build());
				if (StringUtils.hasText(dto.getBan3())) game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(findOrCreateChampion(normalizeChampionName(dto.getBan3()))).build());
				if (StringUtils.hasText(dto.getBan4())) game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(findOrCreateChampion(normalizeChampionName(dto.getBan4()))).build());
				if (StringUtils.hasText(dto.getBan5())) game.getBans().add(Ban.builder().game(game).team(team).bannedChampion(findOrCreateChampion(normalizeChampionName(dto.getBan5()))).build());
			}
		}

		gameParticipantRepository.saveAll(participantsToSave);

		log.info("Processed a chunk of {} records.", chunk.size());
	}

	private String normalizeChampionName(String championName) {
		if (!StringUtils.hasText(championName)) {
			return championName; // null 또는 빈 문자열은 그대로 반환
		}
		return championName.replaceAll("[\\s'.-]", "");
	}

	private League findOrCreateLeague(GameDataCsvDto dto) {
		return leagueRepository.findByLeagueNameAndSeasonYearAndSeasonSplitAndIsPlayoffs(
			dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1
		).orElseGet(() -> {
			log.debug("Creating new league: {}", dto.getLeague());
			League newLeague = League.builder()
				.leagueName(dto.getLeague())
				.seasonYear(dto.getYear())
				.seasonSplit(dto.getSplit())
				.isPlayoffs(dto.getPlayoffs() == 1)
				.build();
			return leagueRepository.save(newLeague);
		});
	}

	private Game findOrCreateGame(GameDataCsvDto dto, League league) {
		return gameRepository.findByGameOriginId(dto.getGameid())
			.orElseGet(() -> gameRepository.save(Game.builder()
			.gameOriginId(dto.getGameid()).league(league)
			.gameDate(LocalDate.parse(dto.getDate(), CSV_DATE_FORMATTER))
			.gameNumber(dto.getGame()).patch(dto.getPatch()).gameLengthSeconds(dto.getGamelength())
			.build()));
	}

	private Team findOrCreateTeam(GameDataCsvDto dto) {
		return teamRepository.findByName(dto.getTeamname())
			.orElseGet(() -> {
				log.debug("Creating new team: {}", dto.getTeamname());
				Team newTeam = Team.builder()
					.name(dto.getTeamname())
					.build();
				return teamRepository.save(newTeam);
			});
	}

	private Player findOrCreatePlayer(GameDataCsvDto dto) {
		return playerRepository.findByName(dto.getPlayername())
			.orElseGet(() -> {
				log.debug("Creating new player: {}", dto.getPlayername());
				Player newPlayer = Player.builder()
					.name(dto.getPlayername())
					.build();
				return playerRepository.save(newPlayer);
			});
	}

	private Champion findOrCreateChampion(String championName) {
		if (!StringUtils.hasText(championName)) {
			return null;
		}
		return championRepository.findByChampionNameEn(championName)
			.orElseThrow(() -> new NoSuchElementException("Champion not found in DB: " + championName));
	}
}