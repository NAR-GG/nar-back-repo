package com.toy.nar.app.kakao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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
		String league = resolveLeague(utterance);
		QueryPeriod period = resolveQueryPeriod(utterance);
		MatchResponseWrapper response = period.isSingleDay()
				? leagueMatchService.getMatchesFromDb(league, period.startDate().toString())
				: leagueMatchService.getMatchesFromDb(league, period.startDate(), period.endDate());
		List<MatchResultDto> sortedMatches = sortMatchesByDate(response.getMatches());
		List<KakaoSkillResponse.Button> buttons = List.of(
				new KakaoSkillResponse.Button("webLink", "NAR 열기", homeUrl()),
				new KakaoSkillResponse.Button("message", nextPromptLabel(period, league), null, nextPromptLabel(period, league)));

		if (sortedMatches == null || sortedMatches.isEmpty()) {
			return KakaoSkillResponse.textCard(
					buildCardTitle(period, league),
					buildCardDescription(period, league, sortedMatches),
					buttons,
					buildQuickReplies(league));
		}

		return KakaoSkillResponse.listCard(
				buildCardTitle(period, league),
				buildListItems(sortedMatches, !period.isSingleDay()),
				buttons,
				buildQuickReplies(league));
	}

	QueryPeriod resolveQueryPeriod(String utterance) {
		LocalDate today = LocalDate.now(KST);
		if (utterance == null || utterance.isBlank()) {
			return QueryPeriod.singleDay(today, labelForSingleDay(today));
		}

		String normalized = utterance.toLowerCase(Locale.ROOT);
		boolean nextWeek = normalized.contains("다음주");
		boolean thisWeek = normalized.contains("이번주");
		boolean weekend = normalized.contains("주말");

		if (weekend) {
			LocalDate baseWeek = nextWeek ? today.plusWeeks(1) : today;
			LocalDate saturday = baseWeek.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()
					? baseWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
					: baseWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
			if (thisWeek || nextWeek) {
				LocalDate weekStart = baseWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				saturday = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
			}
			LocalDate sunday = saturday.plusDays(1);
			return QueryPeriod.range(saturday, sunday, formatWeekendLabel(saturday, sunday));
		}

		if (nextWeek) {
			LocalDate base = today.plusWeeks(1);
			LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			LocalDate end = start.plusDays(6);
			return QueryPeriod.range(start, end, formatWeekLabel("다음주", start, end));
		}

		if (thisWeek) {
			LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			LocalDate end = start.plusDays(6);
			return QueryPeriod.range(start, end, formatWeekLabel("이번주", start, end));
		}

		LocalDate targetDate = resolveTargetDate(normalized, today);
		return QueryPeriod.singleDay(targetDate, labelForSingleDay(targetDate));
	}

	LocalDate resolveTargetDate(String utterance) {
		return resolveTargetDate(utterance, LocalDate.now(KST));
	}

	private LocalDate resolveTargetDate(String utterance, LocalDate today) {
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

	String buildCardTitle(QueryPeriod period, String league) {
		return period.label() + " " + league + " 일정";
	}

	String buildCardDescription(QueryPeriod period, String league, List<MatchResultDto> matches) {
		if (matches == null || matches.isEmpty()) {
			return "%s %s 경기가 없습니다.".formatted(period.label(), league);
		}

		String body = matches.stream()
				.limit(MAX_MATCHES_IN_CARD)
				.map(match -> formatMatchLine(match, !period.isSingleDay()))
				.reduce((left, right) -> left + "\n" + right)
				.orElse("%s %s 경기가 없습니다.".formatted(period.label(), league));

		if (matches.size() > MAX_MATCHES_IN_CARD) {
			return body + "\n외 " + (matches.size() - MAX_MATCHES_IN_CARD) + "경기";
		}
		return body;
	}

	private String formatMatchLine(MatchResultDto match, boolean includeDate) {
		String time = formatMatchTime(match.getMatchDate(), includeDate);
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
				new KakaoSkillResponse.QuickReply("message", "이번주 " + league, "이번주 " + league + " 일정"),
				new KakaoSkillResponse.QuickReply("message", "주말 " + league, "주말 " + league + " 일정"));
	}

	private List<KakaoSkillResponse.ListItem> buildListItems(List<MatchResultDto> matches, boolean includeDate) {
		return matches.stream()
				.limit(MAX_MATCHES_IN_CARD)
				.map(match -> new KakaoSkillResponse.ListItem(
						teamLabel(match.getBlueTeam()) + " vs " + teamLabel(match.getRedTeam()),
						buildItemDescription(match, includeDate),
						representativeImageUrl(match),
						new KakaoSkillResponse.Link(homeUrl())))
				.toList();
	}

	private String buildItemDescription(MatchResultDto match, boolean includeDate) {
		String schedule = formatMatchTime(match.getMatchDate(), includeDate);
		String state = switch (normalizeState(match.getState())) {
			case "completed" -> scoreText(match);
			case "inProgress" -> "진행 중 " + scoreText(match);
			default -> "예정";
		};
		return schedule + " · " + state;
	}

	private String formatMatchTime(String matchDate, boolean includeDate) {
		if (matchDate == null || matchDate.isBlank()) {
			return "--:--";
		}

		LocalDateTime utcTime = LocalDateTime.parse(matchDate);
		LocalDateTime kstTime = utcTime.atZone(ZoneId.of("UTC"))
				.withZoneSameInstant(KST)
				.toLocalDateTime();
		if (includeDate) {
			return kstTime.toLocalDate().format(KAKAO_DATE_FORMAT) + " " + kstTime.toLocalTime().format(KST_TIME_FORMAT);
		}
		return kstTime.toLocalTime().format(KST_TIME_FORMAT);
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

	private String representativeImageUrl(MatchResultDto match) {
		if (match.getBlueTeam() != null && match.getBlueTeam().getImageUrl() != null
				&& !match.getBlueTeam().getImageUrl().isBlank()) {
			return match.getBlueTeam().getImageUrl();
		}
		if (match.getRedTeam() != null && match.getRedTeam().getImageUrl() != null
				&& !match.getRedTeam().getImageUrl().isBlank()) {
			return match.getRedTeam().getImageUrl();
		}
		return null;
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

	private List<MatchResultDto> sortMatchesByDate(List<MatchResultDto> matches) {
		if (matches == null) {
			return List.of();
		}

		return matches.stream()
				.sorted((left, right) -> parseMatchDate(left.getMatchDate()).compareTo(parseMatchDate(right.getMatchDate())))
				.toList();
	}

	private LocalDateTime parseMatchDate(String matchDate) {
		if (matchDate == null || matchDate.isBlank()) {
			return LocalDateTime.MAX;
		}
		return LocalDateTime.parse(matchDate);
	}

	private String formatWeekLabel(String prefix, LocalDate start, LocalDate end) {
		return "%s (%s-%s)".formatted(prefix, start.format(KAKAO_DATE_FORMAT), end.format(KAKAO_DATE_FORMAT));
	}

	private String formatWeekendLabel(LocalDate start, LocalDate end) {
		return "주말 (%s-%s)".formatted(start.format(KAKAO_DATE_FORMAT), end.format(KAKAO_DATE_FORMAT));
	}

	private String labelForSingleDay(LocalDate date) {
		return date.format(KAKAO_DATE_FORMAT);
	}

	private String nextPromptLabel(QueryPeriod period, String league) {
		if (period.isWeekend()) {
			return "다음주 " + league + " 일정";
		}
		if (period.isWeekRange()) {
			return "주말 " + league + " 일정";
		}
		return "내일 " + league + " 일정";
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

	record QueryPeriod(
			LocalDate startDate,
			LocalDate endDate,
			String label,
			Kind kind
	) {
		static QueryPeriod singleDay(LocalDate date, String label) {
			return new QueryPeriod(date, date, label, Kind.SINGLE_DAY);
		}

		static QueryPeriod range(LocalDate startDate, LocalDate endDate, String label) {
			return new QueryPeriod(startDate, endDate, label, Kind.RANGE);
		}

		boolean isSingleDay() {
			return kind == Kind.SINGLE_DAY;
		}

		boolean isWeekend() {
			return label.startsWith("주말");
		}

		boolean isWeekRange() {
			return !isSingleDay() && !isWeekend();
		}
	}

	enum Kind {
		SINGLE_DAY,
		RANGE
	}
}
