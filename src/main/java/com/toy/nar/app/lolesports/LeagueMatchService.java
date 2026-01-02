package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueMatchService {

	private final LeagueMatchRepository leagueMatchRepository;
	private final WorldsService worldsService;
	private final ObjectMapper objectMapper;

	public static final List<String> TARGET_LEAGUES = List.of("LCK", "LPL", "LEC", "LCS", "MSI", "WORLDS");

	// [Scheduler용] 특정 리그의 최신 경기를 가져와 DB에 저장 (1페이지)
	@Transactional
	public void syncMatches(String leagueSlug) {
		log.info("Starting sync for league: {}", leagueSlug);
		// 1. 외부 API에서 데이터 가져오기 (1페이지 분량, pageToken=null)
		MatchResponseWrapper response = worldsService.getWorldsMatches(null, leagueSlug);
		List<MatchResultDto> matches = response.getMatches();

		if (matches.isEmpty()) {
			log.info("No matches found for league: {}", leagueSlug);
			return;
		}

		// 2. DB에 저장 (Upsert)
		for (MatchResultDto dto : matches) {
			try {
				LeagueMatch entity = convertToEntity(dto, leagueSlug);
				leagueMatchRepository.save(entity);
			} catch (Exception e) {
				log.error("Failed to save match: {}", dto.getMatchId(), e);
			}
		}
		log.info("Synced {} matches for league: {}", matches.size(), leagueSlug);
	}

	// [Admin용] 모든 대상 리그의 전체 과거 데이터 동기화
	@Transactional
	public int syncAllLeaguesFullHistory() {
		log.info("Starting FULL history sync for ALL target leagues: {}", TARGET_LEAGUES);
		int totalSynced = 0;
		for (String league : TARGET_LEAGUES) {
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
	@Transactional
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
						leagueMatchRepository.save(entity);
						totalSynced++;
					} catch (Exception e) {
						log.error("Failed to save match: {}", dto.getMatchId(), e);
					}
				}

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

				log.info("Searching matches for league: {} between {} and {}", isAllLeagues ? "ALL" : leagueSlug, start, end);
				
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
				entities = leagueMatchRepository.findAll(
					org.springframework.data.domain.PageRequest.of(0, 50, 
						org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "matchDate"))
				).getContent();
			} else {
				// 특정 리그 최신순 50개
				entities = leagueMatchRepository.findByLeagueNameOrderByMatchDateDesc(
					leagueSlug, org.springframework.data.domain.PageRequest.of(0, 50));
			}
		}

		List<MatchResultDto> dtos = entities.stream()
			.map(this::convertToDto)
			.collect(Collectors.toList());

		return MatchResponseWrapper.builder()
			.matches(dtos)
			.nextPageToken(null)
			.build();
	}
	
	@Transactional(readOnly = true)
	public List<MatchResultDto> getRecentMatchesFromDb(String leagueSlug) {
		List<LeagueMatch> entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		if (entities.isEmpty()) {
			// DB에 없으면(초기 긁어오기 시도
			syncMatches(leagueSlug);
			entities = leagueMatchRepository.findTop3ByLeagueNameOrderByMatchDateDesc(leagueSlug);
		}
		return entities.stream().map(this::convertToDto).collect(Collectors.toList());
	}

	private LeagueMatch convertToEntity(MatchResultDto dto, String leagueSlug) throws JsonProcessingException {
		// "2026-01-05T17:00:00Z" -> LocalDateTime 파싱
		// 라이엇 API 날짜 포맷은 ISO-8601 (ex: 2024-10-19T12:00:00Z)
		LocalDateTime matchDate = LocalDateTime.parse(dto.getMatchDate(), DateTimeFormatter.ISO_DATE_TIME);

		String jsonDetails = objectMapper.writeValueAsString(dto.getSets());
		boolean hasVod = dto.getSets() != null && !dto.getSets().isEmpty() 
						 && dto.getSets().stream().anyMatch(s -> s.getVodUrl() != null && !s.getVodUrl().isEmpty());

		return LeagueMatch.builder()
			.id(dto.getMatchId())
			.leagueName(leagueSlug)
			.matchTitle(dto.getMatchTitle())
			.matchDate(matchDate)
			.state("completed") // 일단 completed만 가져오므로 고정, 필요시 DTO에 state 추가
			.blueTeamCode(dto.getBlueTeam().getCode())
			.blueTeamName(dto.getBlueTeam().getName())
			.blueScore(dto.getBlueTeam().getWins())
			.redTeamCode(dto.getRedTeam().getCode())
			.redTeamName(dto.getRedTeam().getName())
			.redScore(dto.getRedTeam().getWins())
			.hasVod(hasVod)
			.matchDetailsJson(jsonDetails)
			.lastUpdated(LocalDateTime.now())
			.build();
	}

	private MatchResultDto convertToDto(LeagueMatch entity) {
		List<MatchResultDto.SetVod> sets = new ArrayList<>();
		try {
			if (entity.getMatchDetailsJson() != null) {
				sets = objectMapper.readValue(entity.getMatchDetailsJson(), new TypeReference<>() {});
			}
		} catch (JsonProcessingException e) {
			log.error("JSON parsing failed for match: {}", entity.getId(), e);
		}

		return MatchResultDto.builder()
			.matchId(entity.getId())
			.leagueName(entity.getLeagueName())
			.matchTitle(entity.getMatchTitle())
			.matchDate(entity.getMatchDate().toString()) // ISO format string
			.score(entity.getBlueScore() + " : " + entity.getRedScore())
			.blueTeam(MatchResultDto.TeamInfo.builder()
				.code(entity.getBlueTeamCode())
				.name(entity.getBlueTeamName())
				.wins(entity.getBlueScore())
				.build())
			.redTeam(MatchResultDto.TeamInfo.builder()
				.code(entity.getRedTeamCode())
				.name(entity.getRedTeamName())
				.wins(entity.getRedScore())
				.build())
			.sets(sets)
			.build();
	}
}
