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
	private final com.toy.nar.domain.participant.repository.TeamRepository teamRepository;
	private final WorldsService worldsService;
	private final ObjectMapper objectMapper;

	public static final List<String> TARGET_LEAGUES = List.of("LCK", "LPL", "LEC", "LCS", "MSI", "WORLDS");

	private static final java.util.Map<String, String> DEFAULT_LIVE_STREAMS = java.util.Map.of(
			"LCK", "https://play.sooplive.co.kr/aflol",
			"LPL", "https://www.twitch.tv/lpl",
			"LEC", "https://www.twitch.tv/lec",
			"LCS", "https://www.twitch.tv/lcs",
			"WORLDS", "https://www.twitch.tv/riotgames",
			"MSI", "https://www.twitch.tv/riotgames");

	// [Scheduler용] 특정 리그의 최신 경기를 가져와 DB에 저장 (1페이지)
	public void syncMatches(String leagueSlug) {
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
				leagueMatchRepository.save(entity);
			} catch (Exception e) {
				log.error("Failed to save match: {}", dto.getMatchId(), e);
			}
		}

		// 3. Team Metadata Sync
		updateTeamMetadataFromMatches(matches);

		log.info("Synced {} matches for league: {}", matches.size(), leagueSlug);
	}

	// [Admin용] 모든 대상 리그의 전체 과거 데이터 동기화
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
				entities = leagueMatchRepository.findAll(
						org.springframework.data.domain.PageRequest.of(0, 50,
								org.springframework.data.domain.Sort
										.by(org.springframework.data.domain.Sort.Direction.DESC, "matchDate")))
						.getContent();
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
				.state(dto.getState()) // [수정] DTO에서 상태 가져오기
				.blueTeamCode(dto.getBlueTeam().getCode())
				.blueTeamName(dto.getBlueTeam().getName())
				.blueTeamImageUrl(dto.getBlueTeam().getImageUrl())
				.blueScore(dto.getBlueTeam().getWins())
				.redTeamCode(dto.getRedTeam().getCode())
				.redTeamName(dto.getRedTeam().getName())
				.redTeamImageUrl(dto.getRedTeam().getImageUrl())
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
				sets = objectMapper.readValue(entity.getMatchDetailsJson(), new TypeReference<>() {
				});
			}
		} catch (JsonProcessingException e) {
			log.error("JSON parsing failed for match: {}", entity.getId(), e);
		}

		String liveStreamUrl = null;
		if ("inProgress".equalsIgnoreCase(entity.getState())) {
			liveStreamUrl = DEFAULT_LIVE_STREAMS.get(entity.getLeagueName().toUpperCase());
		}

		return MatchResultDto.builder()
				.matchId(entity.getId())
				.leagueName(entity.getLeagueName())
				.matchTitle(entity.getMatchTitle())
				.matchDate(entity.getMatchDate().toString()) // ISO format string
				.state(entity.getState()) // [수정] Entity 상태 DTO로 전달
				.score(entity.getBlueScore() + " : " + entity.getRedScore())
				.blueTeam(MatchResultDto.TeamInfo.builder()
						.code(entity.getBlueTeamCode())
						.name(entity.getBlueTeamName())
						.imageUrl(entity.getBlueTeamImageUrl())
						.wins(entity.getBlueScore())
						.build())
				.redTeam(MatchResultDto.TeamInfo.builder()
						.code(entity.getRedTeamCode())
						.name(entity.getRedTeamName())
						.imageUrl(entity.getRedTeamImageUrl())
						.wins(entity.getRedScore())
						.build())
				.sets(sets)
				.liveStreamUrl(liveStreamUrl)
				.build();
	}

	@Transactional
	protected void updateTeamMetadataFromMatches(List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty())
			return;

		// 1. Collect unique team metadata
		// Map<String(NormalizedName), TeamInfo>
		java.util.Map<String, MatchResultDto.TeamInfo> teamInfoMap = new java.util.HashMap<>();

		java.util.function.Consumer<MatchResultDto.TeamInfo> collector = info -> {
			if (info != null && info.getName() != null) {
				String normName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(info.getName());
				// Only if code or image is present, we consider it efficient metadata source
				if ((info.getCode() != null && !info.getCode().isEmpty()) ||
						(info.getImageUrl() != null && !info.getImageUrl().isEmpty())) {
					teamInfoMap.putIfAbsent(normName, info);
				}
			}
		};

		for (MatchResultDto match : matches) {
			collector.accept(match.getBlueTeam());
			collector.accept(match.getRedTeam());
		}

		if (teamInfoMap.isEmpty())
			return;

		// 2. Fetch existing Teams
		List<com.toy.nar.domain.participant.entity.Team> existingTeams = teamRepository
				.findAllByNameInIgnoreCase(teamInfoMap.keySet());

		// 3. Update Metadata
		for (com.toy.nar.domain.participant.entity.Team team : existingTeams) {
			String normName = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(team.getName());

			MatchResultDto.TeamInfo info = teamInfoMap.get(normName);
			if (info != null) {
				boolean updated = false;
				String newCode = info.getCode();
				String newImage = info.getImageUrl();

				// Only update if valuable
				if (newCode != null && !newCode.isEmpty()) {
					if (!newCode.equals(team.getCode()))
						updated = true;
				} else {
					newCode = team.getCode(); // Keep old
				}

				if (newImage != null && !newImage.isEmpty()) {
					if (!newImage.equals(team.getImageUrl()))
						updated = true;
				} else {
					newImage = team.getImageUrl(); // Keep old
				}

				if (updated) {
					team.updateMetadata(newCode, newImage);
				}
			}
		}
	}
}
