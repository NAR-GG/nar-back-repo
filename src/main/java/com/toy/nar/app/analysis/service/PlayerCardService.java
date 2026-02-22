package com.toy.nar.app.analysis.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.analysis.dto.PlayerCardChampionDto;
import com.toy.nar.app.analysis.dto.PlayerCardFilterDto;
import com.toy.nar.app.analysis.dto.PlayerCardItemDto;
import com.toy.nar.app.analysis.dto.PlayerCardListResponse;
import com.toy.nar.app.analysis.dto.PlayerCardProfileDto;
import com.toy.nar.domain.game.repository.GameParticipantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerCardService {

	private static final int MOST_CHAMPION_LIMIT = 3;
	private static final int MAX_PAGE_SIZE = 100;
	private static final String DDRAGON_LOADING_URL_FORMAT = "https://ddragon.leagueoflegends.com/cdn/img/champion/loading/%s_0.jpg";

	private final GameParticipantRepository gameParticipantRepository;
	private final ObjectMapper objectMapper;

	public PlayerCardListResponse getPlayerCards(String league, Integer year, String split, String patch, String side,
			Integer page, Integer size) {
		String leagueName = normalizeLeague(league);
		Integer normalizedYear = year != null ? year : 2026;
		String normalizedSplit = normalizeBlank(split);
		String normalizedPatch = normalizeBlank(patch);
		String normalizedSide = normalizeSide(side);

		int normalizedPage = page == null || page < 1 ? 1 : page;
		int normalizedSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
		int offset = (normalizedPage - 1) * normalizedSize;

		long totalCount = gameParticipantRepository.countDistinctPlayersByFilter(
				leagueName, normalizedYear, normalizedSplit, normalizedPatch, normalizedSide);

		List<Object[]> summaryRows = gameParticipantRepository.findPlayerCardSummariesByFilter(
				leagueName, normalizedYear, normalizedSplit, normalizedPatch, normalizedSide, normalizedSize, offset);

		List<Long> playerIds = summaryRows.stream()
				.map(row -> toLong(row[0]))
				.toList();

		Map<Long, List<PlayerCardChampionDto>> championsByPlayer = buildMostChampions(
				playerIds, leagueName, normalizedYear, normalizedSplit, normalizedPatch, normalizedSide);

		List<PlayerCardItemDto> players = summaryRows.stream()
				.map(row -> toPlayerCardItem(row, championsByPlayer.getOrDefault(toLong(row[0]), List.of())))
				.toList();

		int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) normalizedSize);

		return PlayerCardListResponse.builder()
				.leagueName(leagueName)
				.appliedFilter(PlayerCardFilterDto.builder()
						.year(normalizedYear)
						.split(normalizedSplit)
						.patch(normalizedPatch)
						.side(normalizedSide != null ? normalizedSide : "ALL")
						.build())
				.page(normalizedPage)
				.size(normalizedSize)
				.totalCount(totalCount)
				.totalPages(totalPages)
				.players(players)
				.build();
	}

	private Map<Long, List<PlayerCardChampionDto>> buildMostChampions(
			List<Long> playerIds, String leagueName, Integer year, String split, String patch, String side) {
		if (playerIds.isEmpty()) {
			return Map.of();
		}

		List<Object[]> rows = gameParticipantRepository.findPlayerMostChampionsByFilter(
				playerIds, leagueName, year, split, patch, side);

		Map<Long, List<PlayerCardChampionDto>> result = new LinkedHashMap<>();
		for (Object[] row : rows) {
			Long playerId = toLong(row[0]);
			List<PlayerCardChampionDto> champions = result.computeIfAbsent(playerId, ignored -> new ArrayList<>());
			if (champions.size() >= MOST_CHAMPION_LIMIT) {
				continue;
			}

			int playCount = toInt(row[5]);
			int wins = toInt(row[6]);
			double winRatePct = playCount > 0 ? (wins * 100.0) / playCount : 0;
			String championImageUrl = toString(row[4]);

			champions.add(PlayerCardChampionDto.builder()
					.championId(toLong(row[1]))
					.championNameKr(toString(row[2]))
					.championNameEn(toString(row[3]))
					.championImageUrl(championImageUrl)
					.championLoadingImageUrl(buildChampionLoadingImageUrl(championImageUrl, toString(row[3])))
					.playCount(playCount)
					.winRatePct(round(winRatePct, 1))
					.build());
		}
		return result;
	}

	private PlayerCardItemDto toPlayerCardItem(Object[] row, List<PlayerCardChampionDto> mostChampions) {
		Long playerId = toLong(row[0]);
		String playerName = toString(row[1]);
		String realName = toString(row[3]);
		String gameAccounts = toString(row[5]);
		int gamesPlayed = toInt(row[10]);
		double totalKills = toDouble(row[11]);
		double totalDeaths = toDouble(row[12]);
		double totalAssists = toDouble(row[13]);
		double kda = (totalKills + totalAssists) / Math.max(totalDeaths, 1.0);

		GameAccount account = extractMainGameAccount(gameAccounts);

		return PlayerCardItemDto.builder()
				.playerId(playerId)
				.playerName(playerName)
				.playerImageUrl(toString(row[2]))
				.teamCode(toString(row[7]))
				.teamImageUrl(toString(row[8]))
				.mostChampions(mostChampions)
				.profile(PlayerCardProfileDto.builder()
						.name(realName != null && !realName.isBlank() ? realName : playerName)
						.position(normalizePosition(toString(row[6])))
						.summonerName(account.summonerName())
						.soloRankTier(account.soloRankTier())
						.birthDate(toString(row[4]))
						.gamesPlayed(gamesPlayed)
						.kda(round(kda, 2))
						.gpm(round(toDouble(row[14]), 1))
						.dpm(round(toDouble(row[15]), 1))
						.build())
				.build();
	}

	private GameAccount extractMainGameAccount(String gameAccountsJson) {
		if (gameAccountsJson == null || gameAccountsJson.isBlank()) {
			return GameAccount.empty();
		}
		try {
			List<Map<String, Object>> accounts = objectMapper.readValue(
					gameAccountsJson, new TypeReference<List<Map<String, Object>>>() {
					});
			if (accounts == null || accounts.isEmpty()) {
				return GameAccount.empty();
			}
			Map<String, Object> main = accounts.get(0);
			return new GameAccount(
					main.get("riotId") == null ? null : main.get("riotId").toString(),
					main.get("tier") == null ? null : main.get("tier").toString());
		} catch (Exception e) {
			return GameAccount.empty();
		}
	}

	private String normalizeLeague(String league) {
		if (league == null || league.isBlank()) {
			return "LCK";
		}
		return league.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeSide(String side) {
		if (side == null || side.isBlank() || "ALL".equalsIgnoreCase(side)) {
			return null;
		}
		return side.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizePosition(String position) {
		if (position == null) {
			return null;
		}
		return switch (position.toLowerCase(Locale.ROOT)) {
			case "jng" -> "JUNGLE";
			case "sup" -> "SUPPORT";
			default -> position.toUpperCase(Locale.ROOT);
		};
	}

	private int toInt(Object value) {
		return value == null ? 0 : ((Number) value).intValue();
	}

	private long toLong(Object value) {
		return value == null ? 0L : ((Number) value).longValue();
	}

	private double toDouble(Object value) {
		return value == null ? 0 : ((Number) value).doubleValue();
	}

	private String toString(Object value) {
		return value == null ? null : value.toString();
	}

	private double round(double value, int scale) {
		double factor = Math.pow(10, scale);
		return Math.round(value * factor) / factor;
	}

	private String buildChampionLoadingImageUrl(String championImageUrl, String championNameEn) {
		String championKey = extractChampionKeyFromImageUrl(championImageUrl);
		if (championKey == null || championKey.isBlank()) {
			championKey = normalizeChampionKey(championNameEn);
		}
		if (championKey == null || championKey.isBlank()) {
			return null;
		}
		return String.format(DDRAGON_LOADING_URL_FORMAT, championKey);
	}

	private String extractChampionKeyFromImageUrl(String championImageUrl) {
		if (championImageUrl == null || championImageUrl.isBlank()) {
			return null;
		}
		int slashIdx = championImageUrl.lastIndexOf('/');
		String fileName = slashIdx >= 0 ? championImageUrl.substring(slashIdx + 1) : championImageUrl;
		int dotIdx = fileName.lastIndexOf('.');
		return dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
	}

	private String normalizeChampionKey(String championNameEn) {
		if (championNameEn == null || championNameEn.isBlank()) {
			return null;
		}
		return championNameEn.replaceAll("[^A-Za-z0-9]", "");
	}

	private record GameAccount(String summonerName, String soloRankTier) {
		private static GameAccount empty() {
			return new GameAccount(null, null);
		}
	}
}
