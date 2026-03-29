package com.toy.nar.app.analysis.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.TeamProfileHeaderResponse;
import com.toy.nar.app.analysis.dto.TeamRecentMatchDto;
import com.toy.nar.app.analysis.dto.TeamSocialLinks;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamProfileService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final TeamRepository teamRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final TeamSocialLinksProvider socialLinksProvider;

	public TeamProfileHeaderResponse getProfileHeader(Long teamId, String league) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

		String leagueName = normalizeLeague(league);
		LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime start = nowUtc.minusDays(30);
		LocalDateTime end = nowUtc.plusDays(30);

		List<LeagueMatch> candidates = leagueMatchRepository.findTeamMatchesInDateRange(
				leagueName,
				team.getName(),
				team.getCode(),
				start,
				end,
				PageRequest.of(0, 40));

		List<TeamRecentMatchDto> recent = buildPreviousTodayNext(candidates, nowUtc);
		TeamSocialLinks socialLinks = socialLinksProvider.getSocialLinks(team.getCode());

		return TeamProfileHeaderResponse.builder()
				.teamId(team.getId())
				.teamName(team.getName())
				.teamCode(team.getCode())
				.teamImageUrl(team.getImageUrl())
				.socialLinks(socialLinks)
				.recentMatches(recent)
				.build();
	}

	private List<TeamRecentMatchDto> buildPreviousTodayNext(List<LeagueMatch> candidates, LocalDateTime nowUtc) {
		LocalDate todayKst = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalDate();

		Optional<LeagueMatch> previous = candidates.stream()
				.filter(m -> toKstDate(m).isBefore(todayKst))
				.max(Comparator.comparing(LeagueMatch::getMatchDate));

		Optional<LeagueMatch> today = candidates.stream()
				.filter(m -> toKstDate(m).isEqual(todayKst))
				.sorted(Comparator
						.comparingInt((LeagueMatch m) -> isLive(m) ? 0 : 1)
						.thenComparingLong(m -> Math.abs(java.time.Duration.between(nowUtc, m.getMatchDate()).toSeconds())))
				.findFirst();

		Optional<LeagueMatch> next = candidates.stream()
				.filter(m -> toKstDate(m).isAfter(todayKst))
				.min(Comparator.comparing(LeagueMatch::getMatchDate));

		List<TeamRecentMatchDto> result = new ArrayList<>(3);
		previous.ifPresent(match -> result.add(toRecentMatchDto(match, "이전")));
		today.ifPresent(match -> result.add(toRecentMatchDto(match, "오늘")));
		next.ifPresent(match -> result.add(toRecentMatchDto(match, "다음")));
		return result;
	}

	private TeamRecentMatchDto toRecentMatchDto(LeagueMatch match, String relativeLabel) {
		LocalDateTime kstDateTime = match.getMatchDate().atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalDateTime();
		return TeamRecentMatchDto.builder()
				.matchId(match.getId())
				.leagueName(match.getLeagueName())
				.state(match.getState())
				.scheduledAt(kstDateTime.format(DATETIME_FORMATTER))
				.relativeLabel(relativeLabel)
				.blueTeamCode(match.getBlueTeamCode())
				.blueTeamName(match.getBlueTeamName())
				.redTeamCode(match.getRedTeamCode())
				.redTeamName(match.getRedTeamName())
				.blueScore(match.getBlueScore())
				.redScore(match.getRedScore())
				.build();
	}

	private boolean isLive(LeagueMatch match) {
		return match.getState() != null && "inProgress".equalsIgnoreCase(match.getState());
	}

	private LocalDate toKstDate(LeagueMatch match) {
		return match.getMatchDate().atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalDate();
	}

	private String normalizeLeague(String league) {
		if (league == null || league.isBlank()) {
			return "LCK";
		}
		return league.trim().toUpperCase(Locale.ROOT);
	}
}
