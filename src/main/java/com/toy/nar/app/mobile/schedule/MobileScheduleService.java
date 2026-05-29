package com.toy.nar.app.mobile.schedule;

import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileScheduleService {

	private static final String DEFAULT_LEAGUE = "LCK";
	private static final int DEFAULT_TEAM_YEAR = 2026;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
	private static final List<String> LCK_TEAM_CODES = List.of(
			"T1", "HLE", "GEN", "DK", "KT",
			"DNS", "BFX", "NS", "BRO", "KRX"
	);

	private final LeagueMatchRepository leagueMatchRepository;
	private final TeamRepository teamRepository;

	public MobileScheduleFilterResponse getFilters(String league) {
		String normalizedLeague = normalizeLeague(league);
		List<MobileScheduleFilterResponse.LeagueOption> leagues = LeagueConstants.ALLOWED_LEAGUES.stream()
				.sorted()
				.map(code -> new MobileScheduleFilterResponse.LeagueOption(code, code))
				.toList();
		List<MobileScheduleFilterResponse.TeamOption> teams = findFilterTeams(normalizedLeague).stream()
				.map(this::toTeamOption)
				.toList();

		return new MobileScheduleFilterResponse(DEFAULT_LEAGUE, leagues, teams);
	}

	public MobileScheduleCalendarResponse getCalendar(YearMonth month, String league, Long teamId) {
		if (month == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		String normalizedLeague = normalizeLeague(league);
		TeamFilter teamFilter = resolveTeamFilter(teamId);
		LocalDateTime startUtc = toUtc(month.atDay(1).atStartOfDay());
		LocalDateTime endUtc = toUtc(month.plusMonths(1).atDay(1).atStartOfDay());
		List<LeagueMatch> matches = findMatches(normalizedLeague, teamFilter, startUtc, endUtc);

		Map<String, MobileScheduleCalendarResponse.DateSummary> summaries = new LinkedHashMap<>();
		for (LeagueMatch match : matches) {
			String date = toKstDate(match).toString();
			MobileScheduleCalendarResponse.DateSummary existing = summaries.get(date);
			if (existing == null) {
				summaries.put(date, new MobileScheduleCalendarResponse.DateSummary(
						date,
						1,
						new ArrayList<>(List.of(toCalendarMatch(match)))));
				continue;
			}
			List<MobileScheduleCalendarResponse.CalendarMatch> dayMatches = new ArrayList<>(existing.matches());
			dayMatches.add(toCalendarMatch(match));
			summaries.put(date, new MobileScheduleCalendarResponse.DateSummary(
					date,
					existing.matchCount() + 1,
					dayMatches));
		}

		return new MobileScheduleCalendarResponse(
				month.toString(),
				normalizedLeague,
				teamId,
				new ArrayList<>(summaries.values()));
	}

	public MobileScheduleListResponse getDailySchedules(LocalDate date, String league, Long teamId) {
		if (date == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		String normalizedLeague = normalizeLeague(league);
		TeamFilter teamFilter = resolveTeamFilter(teamId);
		LocalDateTime startUtc = toUtc(date.atStartOfDay());
		LocalDateTime endUtc = toUtc(date.plusDays(1).atStartOfDay());
		List<MobileScheduleListResponse.MobileMatchSummary> matches = findMatches(
				normalizedLeague,
				teamFilter,
				startUtc,
				endUtc).stream()
				.map(this::toMatchSummary)
				.toList();

		return new MobileScheduleListResponse(date.toString(), normalizedLeague, teamId, matches);
	}

	private List<LeagueMatch> findMatches(
			String league,
			TeamFilter teamFilter,
			LocalDateTime startUtc,
			LocalDateTime endUtc) {
		if (teamFilter == null) {
			return leagueMatchRepository.findMobileMatchesInRange(league, startUtc, endUtc);
		}
		return leagueMatchRepository.findMobileTeamMatchesInRange(
				league,
				teamFilter.name(),
				teamFilter.code(),
				startUtc,
				endUtc);
	}

	private MobileScheduleFilterResponse.TeamOption toTeamOption(Team team) {
		return new MobileScheduleFilterResponse.TeamOption(
				team.getId(),
				team.getName(),
				team.getCode(),
				team.getImageUrl());
	}

	private List<Team> findFilterTeams(String league) {
		if (DEFAULT_LEAGUE.equals(league)) {
			Map<String, Team> teamsByCode = teamRepository.findAllByCodeIn(LCK_TEAM_CODES).stream()
					.collect(Collectors.toMap(Team::getCode, Function.identity(), (left, right) -> left));
			return LCK_TEAM_CODES.stream()
					.map(teamsByCode::get)
					.filter(team -> team != null)
					.toList();
		}

		return teamRepository.findOnboardingTeams(league, DEFAULT_TEAM_YEAR).stream()
				.sorted(Comparator.comparing(Team::getName))
				.toList();
	}

	private MobileScheduleListResponse.MobileMatchSummary toMatchSummary(LeagueMatch match) {
		return new MobileScheduleListResponse.MobileMatchSummary(
				match.getId(),
				toScheduledTime(match),
				match.getState(),
				match.getMatchTitle(),
				normalizeLeague(match.getLeagueName()),
				toTeamResult(
						match.getBlueTeamName(),
						match.getBlueTeamCode(),
						match.getBlueTeamImageUrl(),
						match.getBlueScore()),
				toTeamResult(
						match.getRedTeamName(),
						match.getRedTeamCode(),
						match.getRedTeamImageUrl(),
						match.getRedScore()),
				liveStreamUrl(match));
	}

	private MobileScheduleCalendarResponse.CalendarMatch toCalendarMatch(LeagueMatch match) {
		String blueTeamCode = match.getBlueTeamCode();
		String redTeamCode = match.getRedTeamCode();
		String blueTeamName = NameNormalizer.normalizeTeamName(match.getBlueTeamName());
		String redTeamName = NameNormalizer.normalizeTeamName(match.getRedTeamName());
		return new MobileScheduleCalendarResponse.CalendarMatch(
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

	private MobileScheduleListResponse.MobileTeamResult toTeamResult(
			String teamName,
			String teamCode,
			String teamImageUrl,
			Integer score) {
		return new MobileScheduleListResponse.MobileTeamResult(
				NameNormalizer.normalizeTeamName(teamName),
				teamCode,
				teamImageUrl,
				score != null ? score : 0);
	}

	private String liveStreamUrl(LeagueMatch match) {
		if ("inProgress".equalsIgnoreCase(match.getState())) {
			return LeagueConstants.getLiveStreamUrl(match.getLeagueName());
		}
		return null;
	}

	private String toScheduledTime(LeagueMatch match) {
		if (match.getMatchDate() == null) {
			return "";
		}
		return match.getMatchDate()
				.atZone(UTC)
				.withZoneSameInstant(KST)
				.toLocalTime()
				.format(TIME_FORMATTER);
	}

	private LocalDate toKstDate(LeagueMatch match) {
		return match.getMatchDate()
				.atZone(UTC)
				.withZoneSameInstant(KST)
				.toLocalDate();
	}

	private LocalDateTime toUtc(LocalDateTime kstDateTime) {
		return kstDateTime.atZone(KST)
				.withZoneSameInstant(UTC)
				.toLocalDateTime();
	}

	private TeamFilter resolveTeamFilter(Long teamId) {
		if (teamId == null) {
			return null;
		}
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		return new TeamFilter(team.getName(), team.getCode());
	}

	private String normalizeLeague(String league) {
		String normalized = league == null || league.isBlank()
				? DEFAULT_LEAGUE
				: league.trim().toUpperCase(Locale.ROOT);
		if (!LeagueConstants.ALLOWED_LEAGUES.contains(normalized)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return normalized;
	}

	private record TeamFilter(String name, String code) {
	}
}
