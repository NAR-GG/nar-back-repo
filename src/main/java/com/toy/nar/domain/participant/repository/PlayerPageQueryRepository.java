package com.toy.nar.domain.participant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 모바일 선수 페이지 읽기 전용 집계. 여러 도메인(구독·응원·팬 평점·CSV 경기 기록·일정)을 가로질러
 * 합계만 뽑아서 엔티티 없이 SQL 로 직접 읽는다.
 *
 * <p>월즈는 테이블마다 이름이 다르다 — 일정·평점(league_match)은 {@code WORLDS},
 * CSV 경기 기록(leagues)은 {@code WLDs}.
 */
@Repository
@RequiredArgsConstructor
public class PlayerPageQueryRepository {

	private static final String SCHEDULE_WORLDS = "WORLDS";
	private static final String CSV_WORLDS = "WLDs";

	private final NamedParameterJdbcTemplate jdbc;

	public long subscriberCount(Long playerId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM member_favorite_player WHERE player_id = :playerId",
				new MapSqlParameterSource("playerId", playerId), Long.class);
	}

	/** 최근 {@code days}일 받은 솔랭 응원 합. 판 단위 누적이라 updated_at(마지막으로 누른 시각)으로 거른다. */
	public long cheersSince(Long playerId, int days) {
		return jdbc.queryForObject("SELECT COALESCE(SUM(cheer_count), 0) FROM player_solo_rank_cheer"
						+ " WHERE player_id = :playerId AND updated_at >= NOW(3) - INTERVAL :days DAY",
				new MapSqlParameterSource().addValue("playerId", playerId).addValue("days", days), Long.class);
	}

	private static final String WORLDS_RATINGS = " FROM live_player_rating r JOIN league_match m ON m.id = r.match_id"
			+ " WHERE r.internal_player_id = :playerId AND m.league_name = '" + SCHEDULE_WORLDS + "'"
			+ " AND m.season_year = :year";

	/** 이번 월즈 팬 평점 점수별 개수. 평점이 없으면 빈 맵. */
	public Map<Integer, Long> worldsRatingDistribution(Long playerId, int year) {
		Map<Integer, Long> result = new HashMap<>();
		jdbc.query("SELECT r.rating, COUNT(*) AS cnt" + WORLDS_RATINGS + " GROUP BY r.rating",
				params(playerId, year), rs -> {
					result.put(rs.getInt("rating"), rs.getLong("cnt"));
				});
		return result;
	}

	public record ReviewRow(int rating, String comment, String teamSide, String matchTitle, String blueTeamCode,
							String redTeamCode, LocalDateTime createdAt) {
	}

	public List<ReviewRow> worldsReviews(Long playerId, int year, int limit) {
		return jdbc.query("SELECT r.rating, r.comment, r.team_side, m.match_title, m.blue_team_code, m.red_team_code,"
						+ " r.created_at" + WORLDS_RATINGS + " AND r.comment IS NOT NULL AND r.comment <> ''"
						+ " ORDER BY r.created_at DESC LIMIT :limit",
				params(playerId, year).addValue("limit", limit),
				(rs, i) -> new ReviewRow(rs.getInt("rating"), rs.getString("comment"), rs.getString("team_side"),
						rs.getString("match_title"), rs.getString("blue_team_code"), rs.getString("red_team_code"),
						rs.getTimestamp("created_at").toLocalDateTime()));
	}

	public long worldsReviewCount(Long playerId, int year) {
		return jdbc.queryForObject("SELECT COUNT(*)" + WORLDS_RATINGS + " AND r.comment IS NOT NULL AND r.comment <> ''",
				params(playerId, year), Long.class);
	}

	public record GameRating(double average, long count, String stage) {
	}

	/** CSV 게임 id → 그 세트의 팬 평점. 라이브 매핑(live_game_mapping)이 풀린 세트만. */
	public Map<Long, GameRating> worldsRatingsByGame(Long playerId, int year) {
		Map<Long, GameRating> result = new HashMap<>();
		jdbc.query("SELECT gm.internal_game_id, AVG(r.rating) AS avg_rating, COUNT(*) AS cnt, MAX(m.match_title) AS title"
						+ " FROM live_player_rating r JOIN league_match m ON m.id = r.match_id"
						+ " JOIN live_game_mapping gm ON gm.live_game_id = r.live_game_id"
						+ " WHERE r.internal_player_id = :playerId AND m.league_name = '" + SCHEDULE_WORLDS + "'"
						+ " AND m.season_year = :year AND gm.internal_game_id IS NOT NULL GROUP BY gm.internal_game_id",
				params(playerId, year), rs -> {
					result.put(rs.getLong("internal_game_id"),
							new GameRating(rs.getDouble("avg_rating"), rs.getLong("cnt"), rs.getString("title")));
				});
		return result;
	}

	public record WorldsGameRow(long gameId, LocalDateTime playedAt, boolean win, String position, long championId,
								String championName, String championImageUrl, int kills, int deaths, int assists,
								Integer cs, Integer damage, String opponentTeamCode, String opponentTeamName,
								String opponentTeamImageUrl) {
	}

	/** 이번 월즈 세트별 기록, 최신순. */
	public List<WorldsGameRow> worldsGames(Long playerId, int year) {
		return jdbc.query("SELECT g.game_id, g.actual_game_start_time, gp.is_win, gp.position, c.champion_id,"
						+ " c.champion_name_kr, c.image_url, s.kills, s.deaths, s.assists, s.total_cs,"
						+ " s.damage_to_champions, ot.team_code AS opp_code, ot.team_name AS opp_name,"
						+ " ot.team_image_url AS opp_image"
						+ " FROM game_participants gp"
						+ " JOIN games g ON g.game_id = gp.game_id"
						+ " JOIN leagues l ON l.league_id = g.league_id"
						+ " JOIN game_player_stat s ON s.game_participant_id = gp.participant_game_id"
						+ " JOIN champions c ON c.champion_id = gp.champion_id"
						+ " LEFT JOIN game_team_stat ots ON ots.game_id = gp.game_id AND ots.team_id <> gp.team_id"
						+ " LEFT JOIN teams ot ON ot.team_id = ots.team_id"
						+ " WHERE gp.player_id = :playerId AND l.league_name = '" + CSV_WORLDS + "'"
						+ " AND l.season_year = :year"
						+ " ORDER BY g.actual_game_start_time DESC",
				params(playerId, year),
				(rs, i) -> new WorldsGameRow(rs.getLong("game_id"),
						rs.getTimestamp("actual_game_start_time").toLocalDateTime(), rs.getBoolean("is_win"),
						rs.getString("position"), rs.getLong("champion_id"), rs.getString("champion_name_kr"),
						rs.getString("image_url"), rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("assists"),
						(Integer) rs.getObject("total_cs"), (Integer) rs.getObject("damage_to_champions"),
						rs.getString("opp_code"), rs.getString("opp_name"), rs.getString("opp_image")));
	}

	/** 포지션 지표 비교용 — 이번 월즈 그 포지션 선수 전원의 합산 지표. */
	public record PositionRow(long playerId, int games, double kda, double killParticipation, double dpm,
							  double cspm, double goldDiffAt15, double vspm) {
	}

	public List<PositionRow> worldsPositionStats(String position, int year) {
		return jdbc.query("SELECT gp.player_id, COUNT(*) AS games,"
						+ " (SUM(s.kills) + SUM(s.assists)) / GREATEST(SUM(s.deaths), 1) AS kda,"
						+ " 100 * (SUM(s.kills) + SUM(s.assists)) / GREATEST(SUM(ts.team_kills), 1) AS kp,"
						+ " AVG(s.dpm) AS dpm, AVG(s.cspm) AS cspm, AVG(s.gold_diff_at15) AS gd15, AVG(s.vspm) AS vspm"
						+ " FROM game_participants gp"
						+ " JOIN games g ON g.game_id = gp.game_id"
						+ " JOIN leagues l ON l.league_id = g.league_id"
						+ " JOIN game_player_stat s ON s.game_participant_id = gp.participant_game_id"
						+ " JOIN game_team_stat ts ON ts.game_id = gp.game_id AND ts.team_id = gp.team_id"
						+ " WHERE gp.position = :position AND l.league_name = '" + CSV_WORLDS + "'"
						+ " AND l.season_year = :year"
						+ " GROUP BY gp.player_id",
				new MapSqlParameterSource().addValue("position", position).addValue("year", year),
				(rs, i) -> new PositionRow(rs.getLong("player_id"), rs.getInt("games"), rs.getDouble("kda"),
						rs.getDouble("kp"), rs.getDouble("dpm"), rs.getDouble("cspm"), rs.getDouble("gd15"),
						rs.getDouble("vspm")));
	}

	public record MatchRow(String id, String leagueName, String matchTitle, LocalDateTime matchDateUtc, Integer bestOf,
						   String blueTeamCode, String blueTeamName, String blueTeamImageUrl,
						   String redTeamCode, String redTeamName, String redTeamImageUrl) {
	}

	/**
	 * 팀의 다음 경기(진행 중 포함). {@code match_date} 는 오프셋 없는 UTC 벽시계라 UTC_TIMESTAMP 와 비교한다.
	 * 진행 중인 경기도 잡히게 6시간 앞까지 본다.
	 */
	public Optional<MatchRow> nextMatch(String teamCode) {
		return jdbc.query("SELECT id, league_name, match_title, match_date, best_of,"
						+ " blue_team_code, blue_team_name, blue_team_image_url,"
						+ " red_team_code, red_team_name, red_team_image_url"
						+ " FROM league_match"
						+ " WHERE (blue_team_code = :code OR red_team_code = :code)"
						+ " AND state <> 'completed' AND match_date >= UTC_TIMESTAMP() - INTERVAL 6 HOUR"
						+ " ORDER BY match_date LIMIT 1",
				new MapSqlParameterSource("code", teamCode),
				(rs, i) -> new MatchRow(rs.getString("id"), rs.getString("league_name"), rs.getString("match_title"),
						rs.getTimestamp("match_date").toLocalDateTime(), (Integer) rs.getObject("best_of"),
						rs.getString("blue_team_code"), rs.getString("blue_team_name"),
						rs.getString("blue_team_image_url"), rs.getString("red_team_code"),
						rs.getString("red_team_name"), rs.getString("red_team_image_url")))
				.stream().findFirst();
	}

	public Optional<String> gameAccountsJson(Long playerId) {
		return jdbc.queryForList("SELECT game_accounts FROM players WHERE player_id = :playerId",
				new MapSqlParameterSource("playerId", playerId), String.class).stream().findFirst();
	}

	private static MapSqlParameterSource params(Long playerId, int year) {
		return new MapSqlParameterSource().addValue("playerId", playerId).addValue("year", year);
	}
}
