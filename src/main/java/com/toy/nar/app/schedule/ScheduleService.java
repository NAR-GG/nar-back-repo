package com.toy.nar.app.schedule;

import com.toy.nar.app.lolesports.MatchDateWindow;
import com.toy.nar.app.lolesports.repository.LeagueMatchGame;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.schedule.dto.*;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {
	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";

	private final ScheduleCacheableService scheduleCacheableService;
	private final MatchDetailCacheableService matchDetailCacheableService;
	private final GameRepository gameRepository;
	private final BanRepository banRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final MatchDetailFinder matchDetailFinder;
	private final TeamRepository teamRepository;

	/**
	 * 일정 조회 공개 메서드.
	 * 날짜에 따라 오늘 또는 과거 일정을 조회하는 캐시 서비스를 호출합니다.
	 */
	public ScheduleResponseDto getDailySchedule(LocalDate date) {
		if (date == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (date.isEqual(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
			return scheduleCacheableService.getTodaySchedule(date);
		}
		return scheduleCacheableService.getPastSchedule(date);
	}

	public ScheduleCalendarResponseDto getMonthlyScheduleCalendar(YearMonth month) {
		return getMonthlyScheduleCalendar(month, null, null);
	}

	public ScheduleCalendarResponseDto getMonthlyScheduleCalendar(YearMonth month, String league, Long teamId) {
		if (month == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String leagueFilter = normalizeLeagueFilter(league);
		Team teamFilter = resolveTeamFilter(teamId);
		// match_date 는 오프셋 없는 UTC 벽시계다(MatchDateWindow 참고). KST 한 달의 경계를
		// 그 기준으로 옮겨야 월 첫날·마지막날 새벽 경기가 옆 달로 새지 않는다.
		LocalDateTime start = MatchDateWindow.startOfDay(month.atDay(1));
		LocalDateTime end = MatchDateWindow.endOfDay(month.atEndOfMonth());

		List<com.toy.nar.app.lolesports.repository.LeagueMatch> matches = leagueMatchRepository
				.findByDateRange(start, end).stream()
				.filter(match -> match.getLeagueName() != null)
				.filter(match -> isAllowedLeague(match.getLeagueName()))
				.filter(match -> leagueFilter == null || leagueFilter.equalsIgnoreCase(match.getLeagueName()))
				.filter(match -> teamFilter == null || matchesTeam(match, teamFilter))
				.sorted(Comparator.comparing(com.toy.nar.app.lolesports.repository.LeagueMatch::getMatchDate,
						Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		Map<String, ScheduleCalendarResponseDto.ScheduleDateSummaryDto> dateMap = new LinkedHashMap<>();
		for (var match : matches) {
			if (match.getMatchDate() == null) {
				continue;
			}
			// toLocalDate() 는 UTC 날짜다 — 그대로 쓰면 KST 새벽 경기가 하루 앞 칸에 찍힌다.
			String dateKey = MatchDateWindow.toKstDate(match.getMatchDate()).toString();
			ScheduleCalendarResponseDto.ScheduleDateSummaryDto existing = dateMap.get(dateKey);
			if (existing == null) {
				List<String> leagues = new ArrayList<>();
				leagues.add(match.getLeagueName().toUpperCase());
				dateMap.put(dateKey, new ScheduleCalendarResponseDto.ScheduleDateSummaryDto(
						dateKey,
						1,
						leagues,
						new ArrayList<>(List.of(toCalendarMatchDto(match)))));
			} else {
				List<String> leagues = new ArrayList<>(existing.leagues());
				String leagueName = match.getLeagueName().toUpperCase();
				if (!leagues.contains(leagueName)) {
					leagues.add(leagueName);
				}
				List<ScheduleCalendarResponseDto.CalendarMatchDto> dayMatches = new ArrayList<>(existing.matches());
				dayMatches.add(toCalendarMatchDto(match));
				dateMap.put(dateKey, new ScheduleCalendarResponseDto.ScheduleDateSummaryDto(
						dateKey,
						existing.matchCount() + 1,
						leagues,
						dayMatches));
			}
		}

		return new ScheduleCalendarResponseDto(month.toString(), new ArrayList<>(dateMap.values()));
	}

	private String normalizeLeagueFilter(String league) {
		if (league == null || league.isBlank()) {
			return null;
		}
		String normalized = league.trim().toUpperCase(Locale.ROOT);
		if (!com.toy.nar.app.lolesports.LeagueConstants.ALLOWED_LEAGUES.contains(normalized)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return normalized;
	}

	private Team resolveTeamFilter(Long teamId) {
		if (teamId == null) {
			return null;
		}
		return teamRepository.findById(teamId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
	}

	private boolean isAllowedLeague(String leagueName) {
		return com.toy.nar.app.lolesports.LeagueConstants.ALLOWED_LEAGUES
				.contains(leagueName.toUpperCase(Locale.ROOT));
	}

	private boolean matchesTeam(
			com.toy.nar.app.lolesports.repository.LeagueMatch match,
			Team team) {
		return equalsIgnoreCase(match.getBlueTeamName(), team.getName())
				|| equalsIgnoreCase(match.getRedTeamName(), team.getName())
				|| equalsIgnoreCase(match.getBlueTeamCode(), team.getCode())
				|| equalsIgnoreCase(match.getRedTeamCode(), team.getCode());
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return left.equalsIgnoreCase(right);
	}

	private ScheduleCalendarResponseDto.CalendarMatchDto toCalendarMatchDto(
			com.toy.nar.app.lolesports.repository.LeagueMatch match) {
		String blueTeamCode = match.getBlueTeamCode();
		String redTeamCode = match.getRedTeamCode();
		String blueTeamName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getBlueTeamName());
		String redTeamName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(match.getRedTeamName());
		return new ScheduleCalendarResponseDto.CalendarMatchDto(
				match.getId(),
				blueTeamCode,
				redTeamCode,
				blueTeamName,
				redTeamName,
				displayTeam(blueTeamCode, blueTeamName) + " vs " + displayTeam(redTeamCode, redTeamName));
	}

	private String displayTeam(String teamCode, String teamName) {
		if (teamCode != null && !teamCode.isBlank()) {
			return teamCode;
		}
		return teamName;
	}

	/**
	 * 매치 상세 정보 조회 서비스
	 */
	@Transactional(readOnly = true)
	public MatchDetailResponseDto getMatchDetail(String matchId) {
		// 1. Try to decode matchId as gameIds (internal DB format)
		Set<Long> gameIds = Collections.emptySet();
		try {
			gameIds = decodeMatchId(matchId);
		} catch (CustomException e) {
			// If decoding fails, it might be a lolesports matchId (string)
			// Proceed to fallback
		}

		if (!gameIds.isEmpty()) {
			// 대표 게임 ID 하나로 날짜를 확인합니다.
			Long representativeGameId = gameIds.iterator().next();
			Optional<Game> gameOpt = gameRepository.findById(representativeGameId);
			if (gameOpt.isPresent()) {
				Game game = gameOpt.get();
				LocalDate matchDate = game.getActualGameStartTime()
						.atZone(ZoneId.of("UTC"))
						.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
						.toLocalDate();

				if (matchDate.isEqual(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
					return matchDetailCacheableService.getTodayMatchDetail(matchId);
				}
				return matchDetailCacheableService.getPastMatchDetail(matchId);
			}
		}

		// 2. Fallback: Check LeagueMatchRepository (Lolesports data)
		return leagueMatchRepository.findById(matchId)
				.map(this::convertLeagueMatchToDetailDto)
				.orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));
	}

	private MatchDetailResponseDto convertLeagueMatchToDetailDto(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch) {

		String normalizedBlueTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getBlueTeamName());
		String normalizedRedTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getRedTeamName());

		String scheduledTime = leagueMatch.getMatchDate() != null
				? MatchDateWindow.toKst(leagueMatch.getMatchDate())
						.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
				: "";

		// Create TeamResultDto for each team
		// Create TeamResultDto for each team
		TeamResultDto teamA = new TeamResultDto(
				normalizedBlueTeamName,
				leagueMatch.getBlueTeamCode(),
				leagueMatch.getBlueTeamImageUrl(),
				leagueMatch.getBlueScore() != null ? leagueMatch.getBlueScore() : 0);
		TeamResultDto teamB = new TeamResultDto(
				normalizedRedTeamName,
				leagueMatch.getRedTeamCode(),
				leagueMatch.getRedTeamImageUrl(),
				leagueMatch.getRedScore() != null ? leagueMatch.getRedScore() : 0);

		// Parse VOD info and find matching games
		List<GameDetailDto> gameDetails = parseGameDetailsFromLeagueMatch(leagueMatch);

		// Determine consistency based on whether any game details have a valid ID
		boolean isSynced = gameDetails.stream().anyMatch(d -> d.id() != null);

		// Create summary
		MatchSummaryDto summary = MatchSummaryDto.builder()
				.matchId(leagueMatch.getId())
				.scheduledTime(scheduledTime)
				.leagueInfo(leagueMatch.getLeagueName())
				.matchTitle(leagueMatch.getMatchTitle())
				.matchStatus(leagueMatch.getState())
				.isSynced(isSynced)
				.teamA(teamA)
				.teamB(teamB)
				.build();

		return new MatchDetailResponseDto(summary, gameDetails);
	}

	private List<GameDetailDto> parseGameDetailsFromLeagueMatch(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch) {

		// 1. Parse VOD info from JSON first
		Map<Integer, String> vodBySetNumber = parseVodMap(leagueMatch.getMatchDetailsJson());
		log.debug("Parsed {} VODs for match {}", vodBySetNumber.size(), leagueMatch.getId());

		// 2. Try to find matching Game data by date and team names
		LocalDateTime matchDate = leagueMatch.getMatchDate();
		if (matchDate == null) {
			log.debug("No matchDate for match {}, returning VOD-only details", leagueMatch.getId());
			return createVodOnlyDetails(leagueMatch, vodBySetNumber);
		}

		MappedGameLookup mappedLookup = findMappedGameIds(leagueMatch);
		if (mappedLookup.complete() && !mappedLookup.gameIds().isEmpty()) {
			log.debug("Resolved {} mapped game IDs for match {} via game_external_identity",
					mappedLookup.gameIds().size(), leagueMatch.getId());
			return buildMergedGameDetails(mappedLookup.gameIds(), vodBySetNumber, "mapped");
		}
		if (mappedLookup.complete() && mappedLookup.gameIds().isEmpty()) {
			log.debug("No tracked game rows for match {}, returning VOD-only details", leagueMatch.getId());
			return createVodOnlyDetails(leagueMatch, vodBySetNumber);
		}

		// LeagueMatch.matchDate is stored as UTC, convert to KST date range for Game
		// query
		// Game.scheduledGameStartTime is in UTC, so we query with UTC range
		LocalDateTime startOfDayUtc = matchDate.toLocalDate().atStartOfDay();
		LocalDateTime endOfDayUtc = startOfDayUtc.plusDays(1);
		log.debug("Searching games from {} to {} (UTC) for match {}", startOfDayUtc, endOfDayUtc, leagueMatch.getId());

		// Get games on this date
		List<ScheduleItemDto> scheduleItems = gameRepository.findScheduleItemsByDate(startOfDayUtc, endOfDayUtc);
		log.debug("Found {} schedule items on this date", scheduleItems.size());

		// Group by team pairs and find matching games
		String blueTeamNormalized = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getBlueTeamName());
		String redTeamNormalized = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getRedTeamName());
		log.debug("Looking for teams: blue='{}' (from '{}'), red='{}' (from '{}')",
				blueTeamNormalized, leagueMatch.getBlueTeamName(),
				redTeamNormalized, leagueMatch.getRedTeamName());

		Set<Long> matchingGameIds = findMatchingGameIds(scheduleItems, blueTeamNormalized, redTeamNormalized);
		log.debug("Found {} matching game IDs: {}", matchingGameIds.size(), matchingGameIds);

		if (matchingGameIds.isEmpty()) {
			log.debug("No matching games found, returning VOD-only details");
			return createVodOnlyDetails(leagueMatch, vodBySetNumber);
		}

		// 3. Get full game details from GameParticipant
		return buildMergedGameDetails(matchingGameIds, vodBySetNumber, "fallback");
	}

	private Map<Integer, String> parseVodMap(String matchDetailsJson) {
		Map<Integer, String> vodMap = new HashMap<>();
		if (matchDetailsJson == null || matchDetailsJson.isBlank()) {
			return vodMap;
		}
		try {
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			List<com.toy.nar.app.lolesports.MatchResultDto.SetVod> sets = objectMapper.readValue(
					matchDetailsJson,
					new com.fasterxml.jackson.core.type.TypeReference<List<com.toy.nar.app.lolesports.MatchResultDto.SetVod>>() {
					});
			for (com.toy.nar.app.lolesports.MatchResultDto.SetVod setVod : sets) {
				vodMap.put(setVod.getSetNumber(), setVod.getVodUrl());
			}
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			log.warn("Failed to parse matchDetailsJson: {}", e.getMessage());
		}
		return vodMap;
	}

	private Set<Long> findMatchingGameIds(List<ScheduleItemDto> scheduleItems, String blueTeam, String redTeam) {
		// Group schedule items by game ID
		Map<Long, Set<String>> teamsByGameId = scheduleItems.stream()
				.collect(Collectors.groupingBy(
						ScheduleItemDto::gameId,
						Collectors.mapping(
								item -> com.toy.nar.common.util.NameNormalizer.normalizeTeamName(item.teamName()),
								Collectors.toSet())));

		// Find games where both teams participated
		return teamsByGameId.entrySet().stream()
				.filter(entry -> {
					Set<String> teams = entry.getValue();
					return teams.contains(blueTeam) && teams.contains(redTeam);
				})
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	private List<GameDetailDto> createVodOnlyDetails(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch,
			Map<Integer, String> vodBySetNumber) {
		List<GameDetailDto> details = new ArrayList<>();
		String blueTeam = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(leagueMatch.getBlueTeamName());
		String redTeam = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(leagueMatch.getRedTeamName());

		for (Map.Entry<Integer, String> entry : vodBySetNumber.entrySet()) {
			details.add(new GameDetailDto(
					null,
					entry.getKey(),
					0,
					entry.getValue(),
					new GameDetailDto.TeamPicksDto(blueTeam, false, Collections.emptyList(), Collections.emptyList()),
					new GameDetailDto.TeamPicksDto(redTeam, false, Collections.emptyList(), Collections.emptyList())));
		}
		return details.stream()
				.sorted(Comparator.comparingInt(GameDetailDto::gameNumber))
				.toList();
	}

	private List<GameDetailDto> buildMergedGameDetails(
			Set<Long> gameIds,
			Map<Integer, String> vodBySetNumber,
			String source) {
		List<GameDetailParticipantRow> participantRows = gameParticipantRepository.findScheduleDetailRowsByGameIds(gameIds);
		log.debug("Retrieved {} participant rows for {} details", participantRows.size(), source);
		List<GameBanRow> banRows = banRepository.findScheduleBanRowsByGameIds(gameIds);
		log.debug("Retrieved {} ban rows for {} details", banRows.size(), source);
		return matchDetailFinder.createGameDetails(participantRows, banRows, vodBySetNumber);
	}

	private MappedGameLookup findMappedGameIds(com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch) {
		List<LeagueMatchGameRepository.MappedGameRow> mappedRows = leagueMatchGameRepository
				.findMappedGameRowsByMatchId(leagueMatch.getId(), LOLESPORTS_SOURCE);
		List<LeagueMatchGameRepository.MappedGameRow> trackedRows = mappedRows.stream()
				.filter(row -> shouldTrackGameRow(leagueMatch, row.getGameOrder()))
				.toList();
		if (trackedRows.isEmpty()) {
			return new MappedGameLookup(new LinkedHashSet<>(), true);
		}

		LinkedHashSet<Long> gameIds = new LinkedHashSet<>();
		for (LeagueMatchGameRepository.MappedGameRow row : trackedRows) {
			if (row.getInternalGameId() == null) {
				return new MappedGameLookup(new LinkedHashSet<>(), false);
			}
			gameIds.add(row.getInternalGameId());
		}
		return new MappedGameLookup(gameIds, true);
	}

	private boolean shouldTrackGameRow(com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch, Integer gameOrder) {
		if (gameOrder == null) {
			return false;
		}
		if ("unstarted".equalsIgnoreCase(leagueMatch.getState())) {
			return false;
		}
		if (!"completed".equalsIgnoreCase(leagueMatch.getState())) {
			return true;
		}
		if (leagueMatch.getBlueScore() == null || leagueMatch.getRedScore() == null) {
			return true;
		}
		int playedSets = leagueMatch.getBlueScore() + leagueMatch.getRedScore();
		return playedSets <= 0 || gameOrder <= playedSets;
	}

	private record MappedGameLookup(LinkedHashSet<Long> gameIds, boolean complete) {
	}

	private Set<Long> decodeMatchId(String matchId) {
		try {
			if (matchId == null || matchId.isBlank()) {
				throw new IllegalArgumentException("Empty matchId");
			}
			byte[] decodedBytes = Base64.getDecoder().decode(matchId);
			String[] idStrings = new String(decodedBytes).split(",");
			return Arrays.stream(idStrings)
					.map(Long::parseLong)
					.collect(Collectors.toSet());
		} catch (IllegalArgumentException e) {
			// Lolesports matchId는 Base64가 아니므로 여기서 실패하는 것은 정상입니다.
			// LeagueMatchRepository fallback으로 처리됩니다.
			log.debug("MatchId '{}' is not Base64 encoded, will try LeagueMatch lookup", matchId);
			throw new CustomException(ErrorCode.INVALID_MATCH_ID);
		} catch (Exception e) {
			log.error("Error decoding matchId: {}", matchId, e);
			throw new CustomException(ErrorCode.INVALID_MATCH_ID);
		}
	}
}
