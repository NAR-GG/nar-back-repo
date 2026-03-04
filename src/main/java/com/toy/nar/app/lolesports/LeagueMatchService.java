package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueMatchService {

	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final com.toy.nar.domain.participant.repository.TeamRepository teamRepository;
	private final WorldsService worldsService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	// [Scheduler용] 특정 리그의 최신 경기를 가져와 DB에 저장 (1페이지)
	public void syncMatches(String leagueSlug) {
		syncMatches(leagueSlug, true);
	}

	// 스케줄러 경로에서는 팀 메타데이터 동기화를 제외해 DB 트래픽을 줄인다.
	public void syncMatchesWithoutTeamMetadata(String leagueSlug) {
		syncMatches(leagueSlug, false);
	}

	public void syncMatches(String leagueSlug, boolean includeTeamMetadataSync) {
		log.info("Starting sync for league: {}", leagueSlug);
		// 1. 외부 API에서 데이터 가져오기 (1페이지 분량, pageToken=null)
		MatchResponseWrapper response = worldsService.getWorldsMatches(null, leagueSlug);
		List<MatchResultDto> matches = response.getMatches();

		if (matches.isEmpty()) {
			log.info("No matches found for league: {}", leagueSlug);
			return;
		}

		// 2. DB에 저장 (Upsert) - 트랜잭션은 repository.save()에서 개별적으로 처리됨
		for (MatchResultDto dto : matches) {
			try {
				LeagueMatch entity = convertToEntity(dto, leagueSlug);
				LeagueMatch saved = leagueMatchRepository.save(entity);
				syncLeagueMatchGames(saved.getId(), dto.getGameIds());
			} catch (Exception e) {
				log.error("Failed to save match: {}", dto.getMatchId(), e);
			}
		}

		if (includeTeamMetadataSync) {
			int updated = updateTeamMetadataFromMatches(matches);
			log.info("Team metadata sync completed during syncMatches. updated={}", updated);
		}

		log.info("Synced {} matches for league: {}", matches.size(), leagueSlug);
	}

	// 팀 메타데이터 동기화는 일 배치 경로에서만 실행
	public int syncTeamMetadataForLeagues(List<String> leagues) {
		if (leagues == null || leagues.isEmpty()) {
			return 0;
		}

		int totalUpdated = 0;
		for (String league : leagues) {
			try {
				MatchResponseWrapper response = worldsService.getWorldsMatches(null, league);
				List<MatchResultDto> matches = response.getMatches();
				if (matches == null || matches.isEmpty()) {
					continue;
				}
				totalUpdated += updateTeamMetadataFromMatches(matches);
			} catch (Exception e) {
				log.warn("Team metadata batch failed for league={}: {}", league, e.getMessage());
			}
		}
		return totalUpdated;
	}

	// [Admin용] 모든 대상 리그의 전체 과거 데이터 동기화
	public int syncAllLeaguesFullHistory() {
		log.info("Starting FULL history sync for ALL target leagues: {}", LeagueConstants.TARGET_LEAGUES);
		int totalSynced = 0;
		for (String league : LeagueConstants.TARGET_LEAGUES) {
			try {
				totalSynced += syncFullHistory(league);
				// 리그 사이에는 넉넉하게 10초 대기 (API 차단 방지)
				Thread.sleep(10000);
			} catch (Exception e) {
				log.error("Failed to sync all history for league: {}", league, e);
			}
		}
		log.info("Completed FULL history sync for ALL leagues. Grand total: {}", totalSynced);
		return totalSynced;
	}

	// [Admin용] 특정 리그의 전체 과거 데이터 동기화
	public int syncFullHistory(String leagueSlug) {
		log.info("Starting FULL history sync for league: {}", leagueSlug);
		String pageToken = null;
		int totalSynced = 0;
		int pageCount = 0;

		while (true) {
			try {
				pageCount++;
				log.info("Fetching page {} for league: {} (token: {})", pageCount, leagueSlug, pageToken);

				MatchResponseWrapper response = worldsService.getWorldsMatches(pageToken, leagueSlug);
				List<MatchResultDto> matches = response.getMatches();

				if (matches == null || matches.isEmpty()) {
					log.info("No more matches found for league: {} at page {}", leagueSlug, pageCount);
					break;
				}

				for (MatchResultDto dto : matches) {
					try {
						LeagueMatch entity = convertToEntity(dto, leagueSlug);
						LeagueMatch saved = leagueMatchRepository.save(entity);
						syncLeagueMatchGames(saved.getId(), dto.getGameIds());
						totalSynced++;
					} catch (Exception e) {
						log.error("Failed to save match: {}", dto.getMatchId(), e);
					}
				}

				// Team Metadata Sync per page
				updateTeamMetadataFromMatches(matches);

				pageToken = response.getNextPageToken();
				if (pageToken == null || pageToken.isEmpty()) {
					log.info("End of pages reached for league: {}", leagueSlug);
					break;
				}

				// API 부하 방지
				Thread.sleep(2000);

			} catch (Exception e) {
				log.error("Error during history sync for league: {} at page {}", leagueSlug, pageCount, e);
				break;
			}
		}

		log.info("Completed FULL history sync for league: {}. Total synced: {}", leagueSlug, totalSynced);
		return totalSynced;
	}

	// [API용] DB에서 특정 리그의 경기 목록 조회 (날짜 필터 추가)
	@Transactional(readOnly = true)
	public MatchResponseWrapper getMatchesFromDb(String leagueSlug, String date) {
		List<LeagueMatch> entities;
		boolean isAllLeagues = leagueSlug == null || leagueSlug.isEmpty() || "ALL".equalsIgnoreCase(leagueSlug);

		if (date != null && !date.isEmpty()) {
			try {
				LocalDateTime start;
				LocalDateTime end;

				if (date.length() == 10) { // YYYY-MM-DD
					java.time.LocalDate localDate = java.time.LocalDate.parse(date);
					start = localDate.atStartOfDay();
					end = localDate.atTime(23, 59, 59);
				} else if (date.length() == 7) { // YYYY-MM
					java.time.YearMonth yearMonth = java.time.YearMonth.parse(date);
					start = yearMonth.atDay(1).atStartOfDay();
					end = yearMonth.atEndOfMonth().atTime(23, 59, 59);
				} else {
					throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD or YYYY-MM");
				}

				log.info("Searching matches for league: {} between {} and {}", isAllLeagues ? "ALL" : leagueSlug, start,
						end);

				if (isAllLeagues) {
					entities = leagueMatchRepository.findByDateRange(start, end);
				} else {
					entities = leagueMatchRepository.findByLeagueNameAndDateRange(leagueSlug, start, end);
				}
			} catch (Exception e) {
				log.error("Date parsing failed for input: {}", date);
				return MatchResponseWrapper.builder().matches(List.of()).build();
			}
		} else {
			// 날짜가 없을 때
			if (isAllLeagues) {
				// 전체 리그 최신순 50개 (findAll + sort)
				entities = leagueMatchRepository
						.findAll(org.springframework.data.domain.PageRequest.of(0, 50,
								org.springframework.data.domain.Sort
										.by(org.springframework.data.domain.Sort.Direction.DESC, "matchDate")))
						.getContent();
			} else {
				// 특정 리그 최신순 50개
				entities = leagueMatchRepository.findByLeagueNameOrderByMatchDateDesc(leagueSlug,
						org.springframework.data.domain.PageRequest.of(0, 50));
			}
		}

		Map<String, List<String>> gameIdsByMatchId = loadGameIdsByMatchIds(entities);
		List<MatchResultDto> dtos = entities.stream()
				.map(entity -> convertToDto(entity, gameIdsByMatchId.getOrDefault(entity.getId(), List.of())))
				.collect(Collectors.toList());

		return MatchResponseWrapper.builder().matches(dtos).nextPageToken(null).build();
	}

	@Transactional(readOnly = true)
	public List<MatchResultDto> getRecentMatchesFromDb(String leagueSlug) {
		List<LeagueMatch> entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		if (entities.isEmpty()) {
			// DB에 없으면(초기 긁어오기 시도
			syncMatches(leagueSlug);
			entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		}
		Map<String, List<String>> gameIdsByMatchId = loadGameIdsByMatchIds(entities);
		return entities.stream()
				.map(entity -> convertToDto(entity, gameIdsByMatchId.getOrDefault(entity.getId(), List.of())))
				.collect(Collectors.toList());
	}

	private LeagueMatch convertToEntity(MatchResultDto dto, String leagueSlug) throws JsonProcessingException {
		// "2026-01-05T17:00:00Z" -> LocalDateTime 파싱
		// 라이엇 API 날짜 포맷은 ISO-8601 (ex: 2024-10-19T12:00:00Z)
		LocalDateTime matchDate = LocalDateTime.parse(dto.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);

		String jsonDetails = objectMapper.writeValueAsString(dto.getSets());
		boolean hasVod = dto.getSets() != null && !dto.getSets().isEmpty()
				&& dto.getSets().stream().anyMatch(s -> s.getVodUrl() != null && !s.getVodUrl().isEmpty());

		return LeagueMatch.builder().id(dto.getMatchId()).leagueName(leagueSlug).matchTitle(dto.getMatchTitle())
				.matchDate(matchDate).state(dto.getState()) // [수정]
															// DTO에서
															// 상태
															// 가져오기
				.blueTeamCode(dto.getBlueTeam().getCode()).blueTeamName(dto.getBlueTeam().getName())
				.blueTeamImageUrl(dto.getBlueTeam().getImageUrl()).blueScore(dto.getBlueTeam().getWins())
				.redTeamCode(dto.getRedTeam().getCode()).redTeamName(dto.getRedTeam().getName())
				.redTeamImageUrl(dto.getRedTeam().getImageUrl()).redScore(dto.getRedTeam().getWins()).hasVod(hasVod)
				.matchDetailsJson(jsonDetails).lastUpdated(LocalDateTime.now()).build();
	}

	private MatchResultDto convertToDto(LeagueMatch entity, List<String> gameIds) {
		List<MatchResultDto.SetVod> sets = new ArrayList<>();
		try {
			if (entity.getMatchDetailsJson() != null) {
				sets = objectMapper.readValue(entity.getMatchDetailsJson(), new TypeReference<>() {
				});
			}
		} catch (JsonProcessingException e) {
			log.error("JSON parsing failed for match: {}", entity.getId(), e);
		}

		String liveStreamUrl = null;
		if ("inProgress".equalsIgnoreCase(entity.getState())) {
			liveStreamUrl = LeagueConstants.getLiveStreamUrl(entity.getLeagueName());
		}

		return MatchResultDto.builder().matchId(entity.getId()).leagueName(entity.getLeagueName())
				.matchTitle(entity.getMatchTitle()).matchDate(entity.getMatchDate().toString()) // ISO
																								// format
																								// string
				.state(entity.getState()) // [수정] Entity 상태 DTO로 전달
				.score(entity.getBlueScore() + " : " + entity.getRedScore())
				.blueTeam(
						MatchResultDto.TeamInfo.builder().code(entity.getBlueTeamCode()).name(entity.getBlueTeamName())
								.imageUrl(entity.getBlueTeamImageUrl()).wins(entity.getBlueScore()).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code(entity.getRedTeamCode()).name(entity.getRedTeamName())
						.imageUrl(entity.getRedTeamImageUrl()).wins(entity.getRedScore()).build())
				.sets(sets).liveStreamUrl(liveStreamUrl).gameIds(gameIds).build();
	}

	private Map<String, List<String>> loadGameIdsByMatchIds(List<LeagueMatch> matches) {
		if (matches == null || matches.isEmpty()) {
			return Map.of();
		}

		List<String> matchIds = matches.stream()
				.map(LeagueMatch::getId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
		if (matchIds.isEmpty()) {
			return Map.of();
		}

		Map<String, List<String>> result = new HashMap<>();
		List<LeagueMatchGame> rows = leagueMatchGameRepository.findAllByLeagueMatchIdsOrderByMatchAndGameOrder(matchIds);
		for (LeagueMatchGame row : rows) {
			String matchId = row.getLeagueMatch().getId();
			result.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(row.getGameId());
		}
		return result;
	}

	protected void syncLeagueMatchGames(String matchId, List<String> gameIds) {
		if (matchId == null || matchId.isBlank()) {
			return;
		}

		transactionTemplate.executeWithoutResult(status -> {
			leagueMatchGameRepository.deleteAllByMatchId(matchId);
			if (gameIds == null || gameIds.isEmpty()) {
				return;
			}

			LeagueMatch matchRef = leagueMatchRepository.getReferenceById(matchId);
			List<LeagueMatchGame> rows = new ArrayList<>();
			int order = 1;
			for (String gameId : gameIds) {
				if (gameId == null || gameId.isBlank()) {
					continue;
				}
				rows.add(new LeagueMatchGame(matchRef, gameId, order++));
			}
			if (!rows.isEmpty()) {
				leagueMatchGameRepository.saveAll(rows);
			}
		});
	}

	public GameIdBackfillResult backfillGameIdsForYear(int year, int limit) {
		LocalDateTime start = java.time.LocalDate.of(year, 1, 1).atStartOfDay();
		LocalDateTime end = java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay().minusNanos(1);

		List<LeagueMatch> candidates = leagueMatchRepository.findByDateRange(start, end);
		if (limit > 0 && candidates.size() > limit) {
			candidates = candidates.subList(0, limit);
		}

		int updated = 0;
		int unchanged = 0;
		int failed = 0;

		for (LeagueMatch match : candidates) {
			try {
				List<String> gameIds = worldsService.getGameIdsByMatchId(match.getId()).stream()
						.filter(gameId -> gameId != null && !gameId.isBlank())
						.toList();
				List<String> currentGameIds = leagueMatchGameRepository.findByLeagueMatch_IdOrderByGameOrderAsc(match.getId())
						.stream()
						.map(LeagueMatchGame::getGameId)
						.toList();

				if (currentGameIds.equals(gameIds)) {
					unchanged++;
				} else {
					syncLeagueMatchGames(match.getId(), gameIds);
					updated++;
				}

				// API 부하 완화
				Thread.sleep(120);
			} catch (Exception e) {
				failed++;
				log.warn("Failed to backfill gameIds for matchId={}: {}", match.getId(), e.getMessage());
			}
		}

		return new GameIdBackfillResult(year, candidates.size(), updated, unchanged, failed);
	}

	public record GameIdBackfillResult(
			int year,
			int targetMatches,
			int updatedMatches,
			int unchangedMatches,
			int failedMatches) {
	}

	private final com.toy.nar.domain.game.repository.GameRepository gameRepository;

	// @Transactional // Removed to avoid holding all entities in memory for the
	// whole duration
	protected int updateTeamMetadataFromMatches(List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty())
			return 0;

		log.info("Starting context-aware team metadata sync for {} matches...", matches.size());

		// Group matches by Date (yyyy-MM-dd) to avoid fetching huge range if dates are
		// scattered
		java.util.Map<java.time.LocalDate, List<MatchResultDto>> matchesByDate = matches.stream()
				.filter(m -> m.getMatchDate() != null)
				.collect(Collectors.groupingBy(m -> {
					try {
						return LocalDateTime.parse(m.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
					} catch (Exception e) {
						return java.time.LocalDate.MIN; // Should filter invalid before, but safe fallback
					}
				}));

		int totalUpdated = 0;
		Map<Long, com.toy.nar.domain.participant.entity.Team> dirtyTeams = new LinkedHashMap<>();

		for (java.util.Map.Entry<java.time.LocalDate, List<MatchResultDto>> entry : matchesByDate.entrySet()) {
			java.time.LocalDate date = entry.getKey();
			List<MatchResultDto> dailyMatches = entry.getValue();

			if (date.equals(java.time.LocalDate.MIN))
				continue;

			// Fetch games for this specific date (+/- 1 day buffer)
			LocalDateTime searchStart = date.atStartOfDay().minusHours(24);
			LocalDateTime searchEnd = date.atTime(23, 59, 59).plusHours(24);

			log.info("Processing {} matches for date: {} (Window: {} - {})", dailyMatches.size(), date, searchStart,
					searchEnd);

			List<com.toy.nar.domain.game.entity.Game> candidateGames = gameRepository
					.findAllWithParticipantsByActualGameStartTimeBetween(searchStart, searchEnd);

			log.debug("Found {} candidate games in DB for date {}.", candidateGames.size(), date);

			for (MatchResultDto match : dailyMatches) {
				totalUpdated += processSingleMatchSync(match, candidateGames, dirtyTeams);
			}
		}

		if (!dirtyTeams.isEmpty()) {
			teamRepository.saveAll(dirtyTeams.values());
		}

		log.info("Team metadata sync completed. Updated {} records, {} unique teams (batch save).",
				totalUpdated, dirtyTeams.size());
		return totalUpdated;
	}

	private int processSingleMatchSync(
			MatchResultDto match,
			List<com.toy.nar.domain.game.entity.Game> candidateGames,
			Map<Long, com.toy.nar.domain.participant.entity.Team> dirtyTeams) {
		try {
			LocalDateTime matchDate = LocalDateTime.parse(match.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);
			String normBlue = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getBlueTeam().getName());
			String normRed = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getRedTeam().getName());

			// Find matching Game
			com.toy.nar.domain.game.entity.Game matchedGame = candidateGames.stream().filter(game -> {
				// Check Date (redundant if window is tight, but safe)
				if (!game.getActualGameStartTime().toLocalDate().equals(matchDate.toLocalDate())) {
					return false;
				}

				// Check Teams
				String gBlue = getTeamNameFromGame(game, "Blue");
				String gRed = getTeamNameFromGame(game, "Red");
				String normGBlue = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gBlue);
				String normGRed = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gRed);

				// Match Logic
				return (normGBlue.equalsIgnoreCase(normBlue) && normGRed.equalsIgnoreCase(normRed))
						|| (normGBlue.equalsIgnoreCase(normRed) && normGRed.equalsIgnoreCase(normBlue));
			}).findFirst().orElse(null);

			if (matchedGame != null) {
				int count = 0;
				count += updateTeamIfMatched(matchedGame, match.getBlueTeam(), dirtyTeams);
				count += updateTeamIfMatched(matchedGame, match.getRedTeam(), dirtyTeams);
				return count;
			}
		} catch (Exception e) {
			log.warn("Error processing match metadata sync for matchId: {}", match.getMatchId(), e);
		}
		return 0;
	}

	private int updateTeamIfMatched(
			com.toy.nar.domain.game.entity.Game game,
			MatchResultDto.TeamInfo info,
			Map<Long, com.toy.nar.domain.participant.entity.Team> dirtyTeams) {
		if (info == null || info.getName() == null)
			return 0;
		if ((info.getCode() == null || info.getCode().isEmpty())
				&& (info.getImageUrl() == null || info.getImageUrl().isEmpty())) {
			return 0;
		}

		String normInfoName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(info.getName());

		// Find the participant team entity + strict match check
		return game.getParticipants().stream().map(p -> p.getTeam()).filter(team -> {
			String normTeamName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(team.getName());
			return normTeamName.equalsIgnoreCase(normInfoName);
		}).findFirst().map(team -> {
			boolean updated = false;
			String newCode = info.getCode();
			String newImage = info.getImageUrl();

			if (newCode != null && !newCode.isEmpty() && !newCode.equals(team.getCode())) {
				updated = true;
			} else {
				newCode = team.getCode();
			}

			if (newImage != null && !newImage.isEmpty() && !newImage.equals(team.getImageUrl())) {
				updated = true;
			} else {
				newImage = team.getImageUrl();
			}

			if (updated) {
				log.info("Syncing metadata for matched team '{}' (Game ID: {})", team.getName(), game.getId());
				team.updateMetadata(newCode, newImage);
				dirtyTeams.put(team.getId(), team);
				return 1;
			}
			return 0;
		}).orElse(0);
	}

	private String getTeamNameFromGame(com.toy.nar.domain.game.entity.Game game, String side) {
		return game.getParticipants().stream().filter(p -> p.getSide().equalsIgnoreCase(side)).findFirst()
				.map(p -> p.getTeam().getName()).orElse("");
	}
}
