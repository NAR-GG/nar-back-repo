package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.repository.PlayerRiotAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V96 이전 솔랭 판에 팀(100/200)과 플랫폼을 채운다 — 선수 페이지 "솔랭에서 만난 선수"가
 * 같은 편/상대를 가르려면 두 선수 행 모두 팀이 있어야 한다. 추적 선수 2명 이상이 함께한 판만 본다.
 *
 * <p>판 행에는 플랫폼이 없어서 그 판 선수들의 계정 플랫폼으로 match-v5 를 차례로 찔러 본다.
 * 판 id 는 플랫폼마다 따로 매겨져 KR 판과 NA1 판 번호가 우연히 겹칠 수 있다 — 매치 참가자에
 * 그 선수 puuid 가 실제로 있는 행만 채운다. 관리자가 한 번 돌리는 일회성 작업이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloRankTeamBackfillService {

	/** 키 한도는 모니터와 나눠 쓴다. 판당 최대 플랫폼 수만큼 부르므로 간격을 둔다. */
	private static final long CALL_GAP_MS = 100;

	private final NamedParameterJdbcTemplate jdbc;
	private final PlayerRiotAccountRepository accountRepository;
	private final RiotApiClient riotApiClient;

	public record Result(int games, int matched, int updatedRows, int riotCalls) {
	}

	public Result backfill(int days, int limit) {
		Map<String, List<Long>> games = new LinkedHashMap<>();
		jdbc.query("SELECT g.game_id, g.player_id FROM player_solo_rank_game g"
						+ " JOIN (SELECT game_id FROM player_solo_rank_game"
						+ "       WHERE detected_at >= NOW() - INTERVAL :days DAY"
						+ "       GROUP BY game_id"
						+ "       HAVING COUNT(DISTINCT player_id) > 1 AND SUM(team_id IS NULL) > 0"
						+ "       ORDER BY MAX(detected_at) DESC LIMIT :limit) t ON t.game_id = g.game_id",
				new MapSqlParameterSource().addValue("days", days).addValue("limit", limit),
				rs -> {
					games.computeIfAbsent(rs.getString("game_id"), key -> new ArrayList<>()).add(rs.getLong("player_id"));
				});
		if (games.isEmpty()) {
			return new Result(0, 0, 0, 0);
		}

		Map<String, Long> playerByPuuid = new HashMap<>();
		Map<Long, Set<String>> platformsByPlayer = new HashMap<>();
		accountRepository.findByPlayerIdIn(games.values().stream().flatMap(List::stream).distinct().toList())
				.forEach(account -> {
					Long playerId = account.getPlayer().getId();
					if (account.getPuuid() != null) {
						playerByPuuid.put(account.getPuuid(), playerId);
					}
					if (account.getPlatform() != null) {
						platformsByPlayer.computeIfAbsent(playerId, key -> new LinkedHashSet<>())
								.add(account.getPlatform().toUpperCase());
					}
				});

		int matched = 0;
		int updated = 0;
		int calls = 0;
		for (Map.Entry<String, List<Long>> game : games.entrySet()) {
			Set<String> platforms = new LinkedHashSet<>();
			game.getValue().forEach(playerId -> platforms.addAll(platformsByPlayer.getOrDefault(playerId, Set.of())));
			for (String platform : platforms) {
				RiotMatchResponse match;
				try {
					calls++;
					Thread.sleep(CALL_GAP_MS);
					match = riotApiClient.getMatch(platform + "_" + game.getKey(), platform);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return new Result(games.size(), matched, updated, calls);
				} catch (RiotApiException e) {
					continue; // 다른 플랫폼 판이다(404) — 다음 플랫폼으로
				}
				if (match.info() == null || match.info().participants() == null) {
					continue;
				}
				int rows = 0;
				for (RiotMatchResponse.Participant participant : match.info().participants()) {
					Long playerId = playerByPuuid.get(participant.puuid());
					if (playerId != null && game.getValue().contains(playerId) && participant.teamId() != null) {
						rows += jdbc.update("UPDATE player_solo_rank_game SET team_id = :teamId, platform = :platform"
										+ " WHERE player_id = :playerId AND game_id = :gameId",
								new MapSqlParameterSource().addValue("teamId", participant.teamId())
										.addValue("platform", platform).addValue("playerId", playerId)
										.addValue("gameId", game.getKey()));
					}
				}
				if (rows > 0) {
					matched++;
					updated += rows;
					break;
				}
			}
		}
		log.info("[solo-rank-team-backfill] days={} games={} matched={} updatedRows={} riotCalls={}",
				days, games.size(), matched, updated, calls);
		return new Result(games.size(), matched, updated, calls);
	}
}
