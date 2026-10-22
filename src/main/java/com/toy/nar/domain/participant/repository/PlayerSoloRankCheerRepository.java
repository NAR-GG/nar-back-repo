package com.toy.nar.domain.participant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 솔랭 응원 누적. 엔티티 없이 SQL 로 직접 다룬다 — 쓰기는 upsert 한 줄이고 읽기는 합산뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class PlayerSoloRankCheerRepository {

	/** 한 판의 식별자. */
	public record GameKey(Long playerId, String gameId) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	/** 회원의 (선수, 판) 행에 횟수를 더한다. 없으면 만든다. */
	public void add(Long playerId, String gameId, Long memberId, int count) {
		jdbc.update("INSERT INTO player_solo_rank_cheer (player_id, game_id, member_id, cheer_count)"
						+ " VALUES (:playerId, :gameId, :memberId, :count)"
						+ " ON DUPLICATE KEY UPDATE cheer_count = cheer_count + VALUES(cheer_count),"
						+ " updated_at = CURRENT_TIMESTAMP(3)",
				new MapSqlParameterSource()
						.addValue("playerId", playerId)
						.addValue("gameId", gameId)
						.addValue("memberId", memberId)
						.addValue("count", count));
	}

	/** 판별 전체 응원 합계. 응원이 없는 판은 키가 없다. */
	public Map<GameKey, Long> totals(Collection<Long> playerIds, Collection<String> gameIds) {
		if (playerIds.isEmpty() || gameIds.isEmpty()) {
			return Map.of();
		}
		Map<GameKey, Long> result = new HashMap<>();
		jdbc.query("SELECT player_id, game_id, SUM(cheer_count) AS total FROM player_solo_rank_cheer"
						+ " WHERE player_id IN (:playerIds) AND game_id IN (:gameIds)"
						+ " GROUP BY player_id, game_id",
				new MapSqlParameterSource().addValue("playerIds", playerIds).addValue("gameIds", gameIds),
				rs -> {
					result.put(new GameKey(rs.getLong("player_id"), rs.getString("game_id")), rs.getLong("total"));
				});
		return result;
	}

	/** 판별 내 응원 횟수. */
	public Map<GameKey, Long> mine(Long memberId, Collection<Long> playerIds, Collection<String> gameIds) {
		if (playerIds.isEmpty() || gameIds.isEmpty()) {
			return Map.of();
		}
		Map<GameKey, Long> result = new HashMap<>();
		jdbc.query("SELECT player_id, game_id, cheer_count FROM player_solo_rank_cheer"
						+ " WHERE member_id = :memberId AND player_id IN (:playerIds) AND game_id IN (:gameIds)",
				new MapSqlParameterSource()
						.addValue("memberId", memberId)
						.addValue("playerIds", playerIds)
						.addValue("gameIds", gameIds),
				rs -> {
					result.put(new GameKey(rs.getLong("player_id"), rs.getString("game_id")),
							rs.getLong("cheer_count"));
				});
		return result;
	}

	// ── 백오피스 응원 탭 ──────────────────────────────────────────────
	// 전부 전체 테이블 GROUP BY 다. 기간(days)은 행의 updated_at(그 판에 마지막으로 누른 시각)으로 거른다 —
	// 판 단위 누적이라 경계에 걸친 판은 통째로 들어간다. 시각 비교는 DB 시계(NOW)로 해 저장 시계와 맞춘다.
	// ponytail: 매 요청 전체 집계. 행이 수십만을 넘어 느려지면 일 단위 롤업 테이블로.

	public record CheerSummary(long totalCount, long memberCount, long playerCount, long gameCount) {
	}

	public record CheerMemberRow(Long memberId, String nickname, String email, long totalCount, long gameCount,
								 long playerCount, String topPlayerName, LocalDateTime firstCheeredAt,
								 LocalDateTime lastCheeredAt) {
	}

	public record CheerPlayerRow(Long playerId, String playerName, String imageUrl, String teamName,
								 long totalCount, long memberCount, long gameCount, LocalDateTime lastCheeredAt) {
	}

	/** 정렬 프로퍼티 → SELECT 별칭. 밖의 값은 SQL 에 못 들어가게 버린다. */
	private static final Map<String, String> MEMBER_SORTS = Map.of(
			"totalCount", "total_count", "gameCount", "game_count", "playerCount", "player_count",
			"firstCheeredAt", "first_at", "lastCheeredAt", "last_at");
	private static final Map<String, String> PLAYER_SORTS = Map.of(
			"totalCount", "total_count", "memberCount", "member_count", "gameCount", "game_count",
			"lastCheeredAt", "last_at");

	private static final String PERIOD = " (:days IS NULL OR c.updated_at >= NOW(3) - INTERVAL :days DAY)";

	public CheerSummary summary(Integer days) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(c.cheer_count), 0) total, COUNT(DISTINCT c.member_id) members,"
						+ " COUNT(DISTINCT c.player_id) players, COUNT(DISTINCT c.player_id, c.game_id) games"
						+ " FROM player_solo_rank_cheer c WHERE" + PERIOD,
				new MapSqlParameterSource("days", days),
				(rs, i) -> new CheerSummary(rs.getLong("total"), rs.getLong("members"), rs.getLong("players"),
						rs.getLong("games")));
	}

	/** 응원한 회원 랭킹. playerId 를 주면 그 선수에게 보낸 응원만 센다. q 는 닉네임·이메일. */
	public Page<CheerMemberRow> members(Integer days, Long playerId, String q, Pageable pageable) {
		String where = " WHERE" + PERIOD
				+ " AND (:playerId IS NULL OR c.player_id = :playerId)"
				+ " AND (:q IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))"
				+ " OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%')))";
		var params = new MapSqlParameterSource("days", days).addValue("playerId", playerId).addValue("q", q)
				.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
		long total = jdbc.queryForObject("SELECT COUNT(DISTINCT c.member_id) FROM player_solo_rank_cheer c"
				+ " JOIN member m ON m.id = c.member_id" + where, params, Long.class);
		List<CheerMemberRow> rows = jdbc.query("SELECT c.member_id, m.name, m.tag, m.email,"
						+ " SUM(c.cheer_count) total_count, COUNT(*) game_count, COUNT(DISTINCT c.player_id) player_count,"
						+ " MIN(c.created_at) first_at, MAX(c.updated_at) last_at,"
						// 가장 많이 응원한 선수. 동률이면 최근에 응원한 쪽.
						+ " (SELECT p.player_name FROM player_solo_rank_cheer c2 JOIN players p ON p.player_id = c2.player_id"
						+ "   WHERE c2.member_id = c.member_id AND (:playerId IS NULL OR c2.player_id = :playerId)"
						+ "   AND (:days IS NULL OR c2.updated_at >= NOW(3) - INTERVAL :days DAY)"
						+ "   GROUP BY c2.player_id, p.player_name ORDER BY SUM(c2.cheer_count) DESC, MAX(c2.updated_at) DESC"
						+ "   LIMIT 1) top_player"
						+ " FROM player_solo_rank_cheer c JOIN member m ON m.id = c.member_id" + where
						+ " GROUP BY c.member_id, m.name, m.tag, m.email"
						+ orderBy(pageable.getSort(), MEMBER_SORTS, "c.member_id")
						+ " LIMIT :limit OFFSET :offset",
				params,
				(rs, i) -> new CheerMemberRow(rs.getLong("member_id"), rs.getString("name") + "#" + rs.getString("tag"),
						rs.getString("email"), rs.getLong("total_count"), rs.getLong("game_count"),
						rs.getLong("player_count"), rs.getString("top_player"),
						rs.getObject("first_at", LocalDateTime.class), rs.getObject("last_at", LocalDateTime.class)));
		return new PageImpl<>(rows, pageable, total);
	}

	/** 응원받은 선수 랭킹. memberId 를 주면 그 회원이 보낸 응원만 센다(가입자 상세). */
	public Page<CheerPlayerRow> players(Integer days, Long memberId, Pageable pageable) {
		String where = " WHERE" + PERIOD + " AND (:memberId IS NULL OR c.member_id = :memberId)";
		var params = new MapSqlParameterSource("days", days).addValue("memberId", memberId)
				.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
		long total = jdbc.queryForObject("SELECT COUNT(DISTINCT c.player_id) FROM player_solo_rank_cheer c"
				+ where, params, Long.class);
		List<CheerPlayerRow> rows = jdbc.query("SELECT c.player_id, p.player_name, p.image_url, t.team_name,"
						+ " SUM(c.cheer_count) total_count, COUNT(DISTINCT c.member_id) member_count,"
						+ " COUNT(DISTINCT c.game_id) game_count, MAX(c.updated_at) last_at"
						+ " FROM player_solo_rank_cheer c JOIN players p ON p.player_id = c.player_id"
						+ " LEFT JOIN teams t ON t.team_id = p.current_team_id" + where
						+ " GROUP BY c.player_id, p.player_name, p.image_url, t.team_name"
						+ orderBy(pageable.getSort(), PLAYER_SORTS, "c.player_id")
						+ " LIMIT :limit OFFSET :offset",
				params,
				(rs, i) -> new CheerPlayerRow(rs.getLong("player_id"), rs.getString("player_name"),
						rs.getString("image_url"), rs.getString("team_name"), rs.getLong("total_count"),
						rs.getLong("member_count"), rs.getLong("game_count"),
						rs.getObject("last_at", LocalDateTime.class)));
		return new PageImpl<>(rows, pageable, total);
	}

	/** 화이트리스트 밖 정렬은 버리고, 없으면 누른 횟수 많은 순. 마지막에 id 로 순서를 고정해 페이지가 흔들리지 않게 한다. */
	static String orderBy(Sort sort, Map<String, String> allowed, String tieBreaker) {
		StringBuilder sql = new StringBuilder();
		for (Sort.Order order : sort) {
			String column = allowed.get(order.getProperty());
			if (column != null) {
				sql.append(sql.isEmpty() ? " ORDER BY " : ", ").append(column).append(order.isAscending() ? " ASC" : " DESC");
			}
		}
		if (sql.isEmpty()) {
			sql.append(" ORDER BY total_count DESC");
		}
		return sql.append(", ").append(tieBreaker).append(" DESC").toString();
	}
}
