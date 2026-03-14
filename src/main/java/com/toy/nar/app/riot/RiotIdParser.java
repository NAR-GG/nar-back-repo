package com.toy.nar.app.riot;

import java.util.Optional;
import java.util.regex.Pattern;

public final class RiotIdParser {

	private static final Pattern TRAILING_TIER_PATTERN = Pattern.compile(
			"\\s+(Challenger|Grandmaster|Master|Diamond|Emerald|Platinum|Gold|Silver|Bronze|Iron)"
					+ "(\\s+(I|II|III|IV|V))?"
					+ "(\\s+\\d{1,3}(,\\d{3})*\\s*LP)?"
					+ ".*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TRAILING_UNRANKED_PATTERN = Pattern.compile(
			"\\s+Unranked(?:\\s+Show\\s+Inactive)?(?:\\s*\\(\\d+\\))?.*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TRAILING_SHOW_INACTIVE_PATTERN = Pattern.compile(
			"\\s+Show\\s+Inactive(?:\\s*\\(\\d+\\))?.*$",
			Pattern.CASE_INSENSITIVE);

	private RiotIdParser() {
	}

	public static Optional<ParsedRiotId> parse(String rawRiotId) {
		if (rawRiotId == null || rawRiotId.isBlank()) {
			return Optional.empty();
		}

		String trimmed = sanitize(rawRiotId);
		int separatorIndex = trimmed.lastIndexOf('#');
		if (separatorIndex <= 0 || separatorIndex == trimmed.length() - 1) {
			return Optional.empty();
		}

		String gameName = trimmed.substring(0, separatorIndex).trim();
		String tagLine = trimmed.substring(separatorIndex + 1).trim();
		if (gameName.isBlank() || tagLine.isBlank()) {
			return Optional.empty();
		}

		return Optional.of(new ParsedRiotId(gameName, tagLine));
	}

	static String sanitize(String rawRiotId) {
		if (rawRiotId == null) {
			return "";
		}
		String trimmed = rawRiotId.trim();
		String sanitized = TRAILING_TIER_PATTERN.matcher(trimmed).replaceFirst("").trim();
		sanitized = TRAILING_UNRANKED_PATTERN.matcher(sanitized).replaceFirst("").trim();
		return TRAILING_SHOW_INACTIVE_PATTERN.matcher(sanitized).replaceFirst("").trim();
	}

	public record ParsedRiotId(String gameName, String tagLine) {
		public String normalizedRiotId() {
			return gameName + "#" + tagLine;
		}
	}
}
