package com.toy.nar.app.schedule;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.schedule.dto.*;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

	private final ScheduleCacheableService scheduleCacheableService;
	private final MatchDetailCacheableService matchDetailCacheableService;
	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final MatchDetailFinder matchDetailFinder;

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

		// Format scheduled time from matchDate (LocalDateTime), converting UTC to KST
		String scheduledTime = leagueMatch.getMatchDate() != null
				? leagueMatch.getMatchDate().atZone(java.time.ZoneId.of("UTC"))
						.withZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
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
		List<GameParticipant> participants = gameParticipantRepository.findGameDetailsByGameIds(matchingGameIds);
		log.debug("Retrieved {} participants for merged details", participants.size());

		// 4. Build GameDetailDto merging VOD with Game data
		return buildMergedGameDetails(participants, vodBySetNumber, blueTeamNormalized, redTeamNormalized);
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
			List<GameParticipant> participants,
			Map<Integer, String> vodBySetNumber,
			String blueTeamName, String redTeamName) {

		return matchDetailFinder.createGameDetails(participants, vodBySetNumber);
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
