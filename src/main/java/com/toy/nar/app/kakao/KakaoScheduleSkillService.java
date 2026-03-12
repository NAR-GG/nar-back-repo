package com.toy.nar.app.kakao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.toy.nar.api.kakao.dto.KakaoSkillRequest;
import com.toy.nar.api.kakao.dto.KakaoSkillResponse;
import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoScheduleSkillService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter KAKAO_DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
	private static final DateTimeFormatter KST_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
	private static final Pattern KOREAN_DATE_PATTERN = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
	private static final int MAX_MATCHES_IN_CARD = 5;
	private static final String DEFAULT_LEAGUE = "LCK";
	private static final Map<String, String> LEAGUE_ALIASES = createLeagueAliases();

	private final LeagueMatchService leagueMatchService;

	@Value("${app.server.url:https://api.nar.kr}")
	private String apiServerUrl = "https://api.nar.kr";

	public KakaoSkillResponse handleSchedule(KakaoSkillRequest request) {
		String utterance = request.utteranceOrEmpty();
		LocalDate targetDate = resolveTargetDate(utterance);
		String league = resolveLeague(utterance);
		MatchResponseWrapper response = leagueMatchService.getMatchesFromDb(league, targetDate.toString());

		return KakaoSkillResponse.textCard(
				buildCardTitle(targetDate, league),
				buildCardDescription(targetDate, league, response.getMatches()),
				List.of(
						new KakaoSkillResponse.Button("webLink", "NAR 열기", homeUrl()),
						new KakaoSkillResponse.Button("message", "내일 " + league + " 일정", null, "내일 " + league + " 일정")),
				buildQuickReplies(league));
	}

	LocalDate resolveTargetDate(String utterance) {
		LocalDate today = LocalDate.now(KST);
		if (utterance == null || utterance.isBlank()) {
			return today;
		}

		String normalized = utterance.toLowerCase(Locale.ROOT);
		if (normalized.contains("모레")) {
			return today.plusDays(2);
		}
		if (normalized.contains("내일")) {
			return today.plusDays(1);
		}
		if (normalized.contains("어제")) {
			return today.minusDays(1);
		}
		if (normalized.contains("오늘")) {
			return today;
		}

		Matcher isoDateMatcher = ISO_DATE_PATTERN.matcher(normalized);
		if (isoDateMatcher.find()) {
			return LocalDate.parse(isoDateMatcher.group(1));
		}

		Matcher koreanDateMatcher = KOREAN_DATE_PATTERN.matcher(normalized);
		if (koreanDateMatcher.find()) {
			int month = Integer.parseInt(koreanDateMatcher.group(1));
			int day = Integer.parseInt(koreanDateMatcher.group(2));
			return LocalDate.of(today.getYear(), month, day);
		}

		return today;
	}

	String resolveLeague(String utterance) {
		if (utterance == null || utterance.isBlank()) {
			return DEFAULT_LEAGUE;
		}

		String normalized = utterance.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> entry : LEAGUE_ALIASES.entrySet()) {
			if (normalized.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return DEFAULT_LEAGUE;
	}

	String buildCardTitle(LocalDate targetDate, String league) {
		return targetDate.format(KAKAO_DATE_FORMAT) + " " + league + " 일정";
	}

	String buildCardDescription(LocalDate targetDate, String league, List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty()) {
			return "%s %s 경기가 없습니다.".formatted(targetDate.format(KAKAO_DATE_FORMAT), league);
		}

		String body = matches.stream()
				.limit(MAX_MATCHES_IN_CARD)
				.map(this::formatMatchLine)
				.reduce((left, right) -> left + "\n" + right)
				.orElse("%s %s 경기가 없습니다.".formatted(targetDate.format(KAKAO_DATE_FORMAT), league));

		if (matches.size() > MAX_MATCHES_IN_CARD) {
			return body + "\n외 " + (matches.size() - MAX_MATCHES_IN_CARD) + "경기";
		}
		return body;
	}

	private String formatMatchLine(MatchResultDto match) {
		String time = formatMatchTime(match.getMatchDate());
		String leftTeam = teamLabel(match.getBlueTeam());
		String rightTeam = teamLabel(match.getRedTeam());

		return switch (normalizeState(match.getState())) {
			case "completed" -> "%s %s %s %s".formatted(time, leftTeam, scoreText(match), rightTeam);
			case "inProgress" -> "%s %s vs %s (진행 중, %s)".formatted(time, leftTeam, rightTeam, scoreText(match));
			default -> "%s %s vs %s".formatted(time, leftTeam, rightTeam);
		};
	}

	private List<KakaoSkillResponse.QuickReply> buildQuickReplies(String league) {
		return List.of(
				new KakaoSkillResponse.QuickReply("message", "오늘 " + league, "오늘 " + league + " 일정"),
				new KakaoSkillResponse.QuickReply("message", "내일 " + league, "내일 " + league + " 일정"),
				new KakaoSkillResponse.QuickReply("message", "오늘 LCK", "오늘 LCK 일정"),
				new KakaoSkillResponse.QuickReply("message", "오늘 LPL", "오늘 LPL 일정"));
	}

	private String formatMatchTime(String matchDate) {
		if (matchDate == null || matchDate.isBlank()) {
			return "--:--";
		}

		LocalDateTime utcTime = LocalDateTime.parse(matchDate);
		return utcTime.atZone(ZoneId.of("UTC"))
				.withZoneSameInstant(KST)
				.toLocalTime()
				.format(KST_TIME_FORMAT);
	}

	private String teamLabel(MatchResultDto.TeamInfo team) {
		if (team == null) {
			return "TBD";
		}
		if (team.getCode() != null && !team.getCode().isBlank()) {
			return team.getCode();
		}
		return team.getName();
	}

	private String scoreText(MatchResultDto match) {
		return match.getScore() != null && !match.getScore().isBlank()
				? match.getScore()
				: "%d : %d".formatted(
						match.getBlueTeam() != null ? match.getBlueTeam().getWins() : 0,
						match.getRedTeam() != null ? match.getRedTeam().getWins() : 0);
	}

	private String normalizeState(String state) {
		return state == null ? "unstarted" : state.toLowerCase(Locale.ROOT);
	}

	private String homeUrl() {
		if (apiServerUrl.contains("nar.kr")) {
			return "https://nar.kr";
		}
		return apiServerUrl;
	}

	private static Map<String, String> createLeagueAliases() {
		Map<String, String> aliases = new LinkedHashMap<>();
		aliases.put("fst", "FIRST_STAND");
		aliases.put("first stand", "FIRST_STAND");
		aliases.put("first_stand", "FIRST_STAND");
		aliases.put("퍼스트 스탠드", "FIRST_STAND");
		aliases.put("퍼스트스탠드", "FIRST_STAND");
		aliases.put("월즈", "WORLDS");
		aliases.put("롤드컵", "WORLDS");
		aliases.put("worlds", "WORLDS");
		aliases.put("msi", "MSI");
		aliases.put("lck", "LCK");
		aliases.put("엘씨케이", "LCK");
		aliases.put("lpl", "LPL");
		aliases.put("엘피엘", "LPL");
		aliases.put("lec", "LEC");
		aliases.put("엘이씨", "LEC");
		aliases.put("lcs", "LCS");
		aliases.put("엘씨에스", "LCS");
		aliases.put("lcp", "LCP");
		aliases.put("cblol", "CBLOL");
		return aliases;
	}
}
