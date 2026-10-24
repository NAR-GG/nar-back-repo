package com.toy.nar.app.mobile.player;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.mobile.player.dto.PlayerPageResponse;
import com.toy.nar.app.mobile.player.dto.PlayerSoloRankResponse;
import com.toy.nar.app.mobile.player.dto.PlayerWorldsResponse;
import com.toy.nar.app.mobile.solorank.MobileSoloRankStatusService;
import com.toy.nar.app.mobile.solorank.SoloRankCheerTotals;
import com.toy.nar.app.mobile.solorank.dto.SoloRankStatusResponse;
import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.app.standings.StandingsBlocks;
import com.toy.nar.app.standings.StandingsService;
import com.toy.nar.app.standings.dto.StandingsResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerPageQueryRepository;
import com.toy.nar.domain.participant.repository.PlayerPageQueryRepository.MatchRow;
import com.toy.nar.domain.participant.repository.PlayerPageQueryRepository.PositionRow;
import com.toy.nar.domain.participant.repository.PlayerPageQueryRepository.WorldsGameRow;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * 모바일 선수 페이지 — 홈 솔랭 카드를 누르면 열린다. 상단(프로필·지금 판·다음 경기·지표)과
 * 월즈 탭·솔랭 탭을 따로 준다. 탭은 눌렀을 때만 부르므로 상단을 가볍게 유지한다.
 */
// 클래스 트랜잭션을 두지 않는다 — 다음 경기의 스위스 전적이 캐시 미스면 lolesports 를 부르는데,
// 그동안 커넥션을 쥐고 있으면 경기 시작 버스트에 Hikari 를 말린다. 조회는 서로 독립이다.
@Service
@RequiredArgsConstructor
public class MobilePlayerPageService {

	// ponytail: 대회 한정 상수. 월즈가 끝나 다른 대회를 보여 줘야 하면 대회 선택을 파라미터로 뺀다.
	static final int WORLDS_SEASON_YEAR = 2026;
	static final int RECENT_GAMES = 20;
	static final int REVIEW_PREVIEW = 3;
	static final int CHEER_DAYS = 7;
	static final int MET_PLAYERS = 10;
	private static final String SWISS = "스위스";
	/** 토너먼트 블록 → 이기면 어디로 가나. */
	private static final Map<String, String> KNOCKOUT_STAKES = Map.of(
			"8강", "이기면 4강 진출", "4강", "이기면 결승 진출", "결승", "이기면 우승");

	private final MobilePlayerSubscriptionService subscriptionService;
	private final MobileSoloRankStatusService soloRankStatusService;
	private final PlayerSoloRankGameRepository gameRepository;
	private final PlayerRiotAccountRepository accountRepository;
	private final SoloRankCheerTotals cheerTotals;
	private final PlayerPageQueryRepository queryRepository;
	private final StandingsService standingsService;
	private final ObjectMapper objectMapper;

	public PlayerPageResponse page(Long playerId, Long memberId) {
		PlayerSubscriptionResponse player = player(playerId, memberId);
		PlayerPageResponse.Live live = soloRankStatusService.playerStatus(memberId, player).live().stream()
				.findFirst()
				.map(game -> new PlayerPageResponse.Live(game.championName(), game.championImageUrl(),
						game.startedAt(), game.platform(), game.cheerTotal(), game.cheerMine()))
				.orElse(null);
		PlayerPageResponse.LastGame lastGame = gameRepository.findRecentFinished(playerId, PageRequest.of(0, 1))
				.stream().findFirst()
				.map(game -> new PlayerPageResponse.LastGame(championName(game.getChampion()),
						championImage(game.getChampion()), game.getWin(), game.getKills(), game.getDeaths(),
						game.getAssists(), game.getDurationSeconds(), offset(game.getGameEndedAt())))
				.orElse(null);
		PlayerPageResponse.NextMatch nextMatch = player.teamCode() == null ? null
				: queryRepository.nextMatch(player.teamCode()).map(match -> nextMatch(match, player.teamCode()))
						.orElse(null);
		Map<Integer, Long> distribution = queryRepository.worldsRatingDistribution(playerId, WORLDS_SEASON_YEAR);
		PlayerPageResponse.Stats stats = new PlayerPageResponse.Stats(
				queryRepository.subscriberCount(playerId),
				queryRepository.cheersSince(playerId, CHEER_DAYS),
				distribution.isEmpty() ? null
						: new PlayerPageResponse.Rating(average(distribution), total(distribution)));
		return new PlayerPageResponse(player.playerId(), player.playerName(), player.playerImageUrl(), player.role(),
				player.teamId(), player.teamCode(), player.teamName(), player.teamImageUrl(), player.subscribed(),
				player.startEnabled(), player.endEnabled(), live, lastGame, nextMatch, stats);
	}

	public PlayerWorldsResponse worlds(Long playerId) {
		PlayerSubscriptionResponse player = player(playerId, null);
		List<WorldsGameRow> rows = queryRepository.worldsGames(playerId, WORLDS_SEASON_YEAR);
		int wins = (int) rows.stream().filter(WorldsGameRow::win).count();

		Map<Long, PlayerPageQueryRepository.GameRating> ratings =
				queryRepository.worldsRatingsByGame(playerId, WORLDS_SEASON_YEAR);
		List<PlayerWorldsResponse.Game> games = rows.stream()
				.map(row -> {
					PlayerPageQueryRepository.GameRating rating = ratings.get(row.gameId());
					return new PlayerWorldsResponse.Game(row.gameId(), row.playedAt().atOffset(ZoneOffset.UTC), row.win(),
							rating == null ? null : StandingsBlocks.blockNameOf(rating.stage()),
							row.opponentTeamCode(), row.opponentTeamName(), row.opponentTeamImageUrl(), row.championName(),
							row.championImageUrl(), row.kills(), row.deaths(), row.assists(), row.cs(), row.damage(),
							rating == null ? null
									: new PlayerPageResponse.Rating(round1(rating.average()), rating.count()));
				})
				.toList();

		return new PlayerWorldsResponse(
				new PlayerWorldsResponse.Record(rows.size(), wins, rows.size() - wins),
				rows.isEmpty() ? List.of() : stats(playerId, rows.get(0).position()),
				games,
				champions(gameRepository.findRecentFinished(playerId, PageRequest.of(0, RECENT_GAMES)), rows),
				worldsRating(playerId, player.teamCode()));
	}

	public PlayerSoloRankResponse soloRank(Long playerId) {
		player(playerId, null);
		LocalDateTime now = LocalDateTime.now();
		List<PlayerSoloRankGame> games = gameRepository.findRecentFinished(playerId, PageRequest.of(0, RECENT_GAMES));
		Map<GameKey, Long> cheers = cheerTotals.finished(
				games.stream().map(game -> new GameKey(playerId, game.getGameId())).toList());
		long lastWeek = gameRepository.countByPlayer_IdAndWinIsNotNullAndDetectedAtGreaterThanEqual(
				playerId, now.minusDays(CHEER_DAYS));
		return new PlayerSoloRankResponse(
				accounts(playerId, now),
				recent(games, lastWeek),
				soloChampions(games),
				games.stream()
						.map(game -> new PlayerSoloRankResponse.Game(game.getGameId(), championName(game.getChampion()),
								championImage(game.getChampion()), game.getWin(), nz(game.getKills()),
								nz(game.getDeaths()), nz(game.getAssists()), game.getDurationSeconds(),
								offset(game.getGameEndedAt()),
								cheers.getOrDefault(new GameKey(playerId, game.getGameId()), 0L)))
						.toList(),
				metPlayers(playerId));
	}

	private List<PlayerSoloRankResponse.MetPlayer> metPlayers(Long playerId) {
		List<PlayerPageQueryRepository.MetRow> rows = queryRepository.metPlayers(playerId, CHEER_DAYS, MET_PLAYERS);
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<Long, PlayerSubscriptionResponse> players = new HashMap<>();
		subscriptionService.getPlayers(null, rows.stream().map(PlayerPageQueryRepository.MetRow::playerId)
						.collect(java.util.stream.Collectors.toSet()))
				.forEach(player -> players.put(player.playerId(), player));
		return rows.stream()
				.filter(row -> players.containsKey(row.playerId()))
				.map(row -> {
					PlayerSubscriptionResponse met = players.get(row.playerId());
					return new PlayerSoloRankResponse.MetPlayer(met.playerId(), met.playerName(),
							met.playerImageUrl(), met.teamCode(), met.teamImageUrl(), row.sameTeamGames(),
							row.sameTeamWins(), row.opponentGames(), row.opponentWins(),
							row.opponentGames() - row.opponentWins(), offset(row.lastMetAt()));
				})
				.toList();
	}

	private PlayerSubscriptionResponse player(Long playerId, Long memberId) {
		return subscriptionService.getPlayers(memberId, Set.of(playerId)).stream().findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
	}

	// ── 상단 ────────────────────────────────────────────────────────

	private PlayerPageResponse.NextMatch nextMatch(MatchRow match, String teamCode) {
		boolean blue = teamCode.equals(match.blueTeamCode());
		String block = StandingsBlocks.blockNameOf(match.matchTitle());
		Map<String, int[]> records = SWISS.equals(block) ? swissRecords(match.leagueName()) : Map.of();
		int[] mine = SWISS.equals(block) ? records.getOrDefault(teamCode, ZERO) : null;
		PlayerPageResponse.MatchTeam blueTeam = matchTeam(match.blueTeamCode(), match.blueTeamName(),
				match.blueTeamImageUrl(), swissRecord(records, block, match.blueTeamCode()));
		PlayerPageResponse.MatchTeam redTeam = matchTeam(match.redTeamCode(), match.redTeamName(),
				match.redTeamImageUrl(), swissRecord(records, block, match.redTeamCode()));
		return new PlayerPageResponse.NextMatch(match.id(), match.leagueName(),
				mine == null ? block : block + " " + (mine[0] + mine[1] + 1) + "라운드",
				match.matchDateUtc().atOffset(ZoneOffset.UTC),
				match.bestOf(),
				blue ? blueTeam : redTeam,
				blue ? redTeam : blueTeam,
				stake(block, mine));
	}

	/** 스위스 버킷에 아직 없는 팀은 첫 경기 전(0-0)이다. */
	private static final int[] ZERO = {0, 0};

	private static int[] swissRecord(Map<String, int[]> records, String block, String code) {
		return SWISS.equals(block) ? records.getOrDefault(code, ZERO) : null;
	}

	/** 스위스는 3승 진출·3패 탈락. 2승이면 이기는 쪽이 진출, 2패면 지는 쪽이 탈락이다. */
	static String stake(String block, int[] record) {
		if (SWISS.equals(block)) {
			if (record == null) {
				return null;
			}
			return record[0] == 2 ? "이기면 8강 진출" : record[1] == 2 ? "지면 탈락" : null;
		}
		return KNOCKOUT_STAKES.get(block);
	}

	/** 팀 코드 → 스위스 현재 전적 {승, 패}. 버킷은 지나온 전적도 남아 있어 가장 많이 치른 버킷을 고른다. */
	private Map<String, int[]> swissRecords(String league) {
		if (!StandingsService.hasScope(league)) {
			return Map.of();
		}
		StandingsResponse standings = standingsService.getStandings(league);
		if (standings.bracket() == null || standings.bracket().swiss() == null) {
			return Map.of();
		}
		Map<String, int[]> records = new HashMap<>();
		for (StandingsResponse.SwissRow row : standings.bracket().swiss()) {
			String[] parts = row.record().split("-");
			int[] record = {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
			row.teamCodes().forEach(code -> records.merge(code, record,
					(left, right) -> left[0] + left[1] >= right[0] + right[1] ? left : right));
		}
		return records;
	}

	private static PlayerPageResponse.MatchTeam matchTeam(String code, String name, String imageUrl, int[] record) {
		return new PlayerPageResponse.MatchTeam(code, name, imageUrl,
				record == null ? null : record[0], record == null ? null : record[1]);
	}

	// ── 월즈 탭 ──────────────────────────────────────────────────────

	private record Metric(String key, ToDoubleFunction<PositionRow> value) {
	}

	private static final List<Metric> METRICS = List.of(
			new Metric("KDA", PositionRow::kda),
			new Metric("KP", PositionRow::killParticipation),
			new Metric("DPM", PositionRow::dpm),
			new Metric("CSPM", PositionRow::cspm),
			new Metric("GD15", PositionRow::goldDiffAt15),
			new Metric("VSPM", PositionRow::vspm));

	/** 같은 포지션 선수들 사이에서의 값·평균·순위. 지표는 모두 클수록 좋다. */
	private List<PlayerWorldsResponse.Stat> stats(Long playerId, String position) {
		List<PositionRow> rows = queryRepository.worldsPositionStats(position, WORLDS_SEASON_YEAR);
		PositionRow mine = rows.stream().filter(row -> row.playerId() == playerId).findFirst().orElse(null);
		if (mine == null) {
			return List.of();
		}
		return METRICS.stream()
				.map(metric -> {
					double value = metric.value().applyAsDouble(mine);
					return new PlayerWorldsResponse.Stat(metric.key(), round1(value),
							round1(rows.stream().mapToDouble(metric.value()).average().orElse(0)),
							1 + (int) rows.stream().filter(row -> metric.value().applyAsDouble(row) > value).count(),
							rows.size());
				})
				.toList();
	}

	/** 솔랭 최근 20판과 월즈 기록의 챔피언 합집합. */
	private static List<PlayerWorldsResponse.Champion> champions(List<PlayerSoloRankGame> solo,
			List<WorldsGameRow> worlds) {
		Map<String, int[]> counts = new LinkedHashMap<>(); // 이름 → {솔승, 솔패, 월승, 월패}
		Map<String, String> images = new HashMap<>();
		solo.stream().filter(game -> game.getChampion() != null).forEach(game -> {
			String name = game.getChampion().getChampionNameKr();
			images.putIfAbsent(name, game.getChampion().getImageUrl());
			counts.computeIfAbsent(name, key -> new int[4])[Boolean.TRUE.equals(game.getWin()) ? 0 : 1]++;
		});
		worlds.forEach(row -> {
			images.putIfAbsent(row.championName(), row.championImageUrl());
			counts.computeIfAbsent(row.championName(), key -> new int[4])[row.win() ? 2 : 3]++;
		});
		return counts.entrySet().stream()
				.map(entry -> new PlayerWorldsResponse.Champion(entry.getKey(), images.get(entry.getKey()),
						entry.getValue()[0], entry.getValue()[1], entry.getValue()[2], entry.getValue()[3]))
				.toList();
	}

	private PlayerWorldsResponse.Rating worldsRating(Long playerId, String teamCode) {
		Map<Integer, Long> distribution = queryRepository.worldsRatingDistribution(playerId, WORLDS_SEASON_YEAR);
		if (distribution.isEmpty()) {
			return null;
		}
		long count = total(distribution);
		List<PlayerWorldsResponse.Bucket> buckets = IntStream.iterate(5, rating -> rating >= 1, rating -> rating - 1)
				.mapToObj(rating -> {
					long bucket = distribution.getOrDefault(rating, 0L);
					return new PlayerWorldsResponse.Bucket(rating, bucket, round1(100.0 * bucket / count));
				})
				.toList();
		List<PlayerWorldsResponse.Review> reviews = queryRepository
				.worldsReviews(playerId, WORLDS_SEASON_YEAR, REVIEW_PREVIEW).stream()
				.map(row -> new PlayerWorldsResponse.Review(row.rating(), row.comment(),
						opponent(row, teamCode),
						StandingsBlocks.blockNameOf(row.matchTitle()), offset(row.createdAt())))
				.toList();
		return new PlayerWorldsResponse.Rating(average(distribution), count, buckets, reviews,
				queryRepository.worldsReviewCount(playerId, WORLDS_SEASON_YEAR));
	}

	/**
	 * 리뷰한 세트의 상대 팀. 진영은 세트마다 바뀌는데 league_match 의 blue/red 는 매치 단위로 고정이라
	 * team_side 로는 못 고른다 — 팀 코드로 맞추고, 코드가 안 맞을 때만 진영으로 짐작한다.
	 */
	static String opponent(PlayerPageQueryRepository.ReviewRow row, String teamCode) {
		if (teamCode != null && teamCode.equals(row.blueTeamCode())) {
			return row.redTeamCode();
		}
		if (teamCode != null && teamCode.equals(row.redTeamCode())) {
			return row.blueTeamCode();
		}
		return "Blue".equalsIgnoreCase(row.teamSide()) ? row.redTeamCode() : row.blueTeamCode();
	}

	// ── 솔랭 탭 ──────────────────────────────────────────────────────

	private List<PlayerSoloRankResponse.Account> accounts(Long playerId, LocalDateTime now) {
		List<Map<String, Object>> crawled = crawledAccounts(playerId);
		return accountRepository.findByPlayerIdAndEnabledTrueOrderByPrimaryAccountDesc(playerId).stream()
				.map(account -> new PlayerSoloRankResponse.Account(account.getPlatform(),
						region(account.getPlatform()), account.getRiotId(), tier(crawled, account.getRiotId()),
						isLive(account, now)))
				.toList();
	}

	private static boolean isLive(PlayerRiotAccount account, LocalDateTime now) {
		return account.getLiveStatus() == PlayerRiotAccountLiveStatus.IN_RANKED_SOLO
				&& account.getLastMatchCheckedAt() != null
				&& !account.getLastMatchCheckedAt().isBefore(now.minus(MobileSoloRankStatusService.LIVE_FRESHNESS));
	}

	/** players.game_accounts 크롤링 JSON. 깨졌으면 없는 셈 친다 — 티어는 곁들이 정보다. */
	private List<Map<String, Object>> crawledAccounts(Long playerId) {
		return queryRepository.gameAccountsJson(playerId)
				.map(json -> {
					try {
						return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
						});
					} catch (Exception e) {
						return List.<Map<String, Object>>of();
					}
				})
				.orElse(List.of());
	}

	/** 크롤링 riotId 는 뒤에 "Unranked" 같은 꼬리가 붙어 있기도 해 접두로 맞춘다. */
	static String tier(List<Map<String, Object>> crawled, String riotId) {
		if (riotId == null) {
			return null;
		}
		String target = riotId.toLowerCase();
		return crawled.stream()
				.filter(entry -> entry.get("riotId") != null && entry.get("tier") != null)
				.filter(entry -> {
					String crawledId = entry.get("riotId").toString().toLowerCase();
					return crawledId.equals(target) || crawledId.startsWith(target + " ");
				})
				.map(entry -> entry.get("tier").toString())
				.findFirst().orElse(null);
	}

	/** NA1 → NA, EUW1 → EUW, KR → KR. */
	static String region(String platform) {
		return platform == null ? null : platform.replaceAll("\\d+$", "");
	}

	static PlayerSoloRankResponse.Recent recent(List<PlayerSoloRankGame> games, long lastWeek) {
		int count = games.size();
		int wins = (int) games.stream().filter(game -> Boolean.TRUE.equals(game.getWin())).count();
		int kills = games.stream().mapToInt(game -> nz(game.getKills())).sum();
		int deaths = games.stream().mapToInt(game -> nz(game.getDeaths())).sum();
		int assists = games.stream().mapToInt(game -> nz(game.getAssists())).sum();
		List<Boolean> results = new ArrayList<>(games.stream().map(PlayerSoloRankGame::getWin).toList());
		java.util.Collections.reverse(results);
		return new PlayerSoloRankResponse.Recent(count, wins, count - wins,
				count == 0 ? 0 : round1((double) kills / count),
				count == 0 ? 0 : round1((double) deaths / count),
				count == 0 ? 0 : round1((double) assists / count),
				kda(kills, deaths, assists),
				round1(lastWeek / (double) CHEER_DAYS),
				(int) games.stream().map(PlayerSoloRankGame::getDurationSeconds).filter(Objects::nonNull)
						.mapToInt(Integer::intValue).average().orElse(0),
				results);
	}

	private static List<PlayerSoloRankResponse.Champion> soloChampions(List<PlayerSoloRankGame> games) {
		Map<Long, List<PlayerSoloRankGame>> byChampion = new LinkedHashMap<>();
		games.stream().filter(game -> game.getChampion() != null)
				.forEach(game -> byChampion.computeIfAbsent(game.getChampion().getId(), key -> new ArrayList<>())
						.add(game));
		return byChampion.values().stream()
				.map(played -> {
					Champion champion = played.get(0).getChampion();
					return new PlayerSoloRankResponse.Champion(champion.getChampionNameKr(),
							champion.getImageUrl(), played.size(),
							(int) played.stream().filter(game -> Boolean.TRUE.equals(game.getWin())).count(),
							kda(played.stream().mapToInt(game -> nz(game.getKills())).sum(),
									played.stream().mapToInt(game -> nz(game.getDeaths())).sum(),
									played.stream().mapToInt(game -> nz(game.getAssists())).sum()));
				})
				.sorted(Comparator.comparingInt(PlayerSoloRankResponse.Champion::games).reversed()
						.thenComparing(Comparator.comparingInt(PlayerSoloRankResponse.Champion::wins).reversed()))
				.limit(5)
				.toList();
	}

	// ── 공용 ────────────────────────────────────────────────────────

	private static double kda(int kills, int deaths, int assists) {
		return round1((kills + assists) / (double) Math.max(deaths, 1));
	}

	private static double average(Map<Integer, Long> distribution) {
		long weighted = distribution.entrySet().stream().mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
		return round1((double) weighted / total(distribution));
	}

	private static long total(Map<Integer, Long> distribution) {
		return distribution.values().stream().mapToLong(Long::longValue).sum();
	}

	private static double round1(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static int nz(Integer value) {
		return value == null ? 0 : value;
	}

	private static String championName(Champion champion) {
		return champion == null ? null : champion.getChampionNameKr();
	}

	private static String championImage(Champion champion) {
		return champion == null ? null : champion.getImageUrl();
	}

	/** 솔랭 이력·평점 시각은 JVM 시간대 벽시계다(CSV 경기 시각은 UTC 라 따로 붙인다). */
	private static OffsetDateTime offset(LocalDateTime value) {
		return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}
}
