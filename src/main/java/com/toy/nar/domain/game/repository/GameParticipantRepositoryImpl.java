package com.toy.nar.domain.game.repository;

import com.toy.nar.app.analysis.dto.CombinationStatDto;
import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GameParticipantRepositoryImpl implements GameParticipantRepositoryCustom {

	private final EntityManager em;

	@Override
	public Page<CombinationStatDto> findCombinationStats(List<String> championNames, MultiCombinationFilterDto filter,
			Pageable pageable) {
		Map<String, Object> params = new HashMap<>();

		// 1) 공통 CTE(valid_teams, team_combinations)
		String baseQuery = buildBaseQuery(championNames, filter, params);

		// 2) total count: 조합 종류 수
		String countSql = baseQuery
				+ " SELECT COUNT(*) FROM (SELECT 1 FROM team_combinations GROUP BY championCombination) AS distinct_combinations";
		Query countQuery = em.createNativeQuery(countSql);
		params.forEach(countQuery::setParameter);
		long total = ((Number) countQuery.getSingleResult()).longValue();

		// 3) 데이터 조회: annotated CTE 추가(윈도우 함수로 최신 패치 추출)
		StringBuilder dataSql = new StringBuilder(baseQuery);
		dataSql.append("""
				, annotated AS (
				    SELECT
				        championCombination,
				        isWin,
				        gameDate,
				        patch,
				        ROW_NUMBER() OVER (
				            PARTITION BY championCombination
				            ORDER BY gameDate DESC
				        ) AS rn
				    FROM team_combinations
				)
				SELECT
				    championCombination,
				    COUNT(*) AS frequency,
				    SUM(isWin) AS winCount,
				    MAX(gameDate) AS latestGameDate,
				    MAX(CASE WHEN rn = 1 THEN patch END) AS latestPatch
				FROM annotated
				GROUP BY championCombination
				""");

		// 정렬 조건
		Sort sort = pageable.getSort();
		if (sort.isSorted()) {
			dataSql.append(" ORDER BY ");
			String orderByClause = sort.stream()
					.map(order -> order.getProperty() + " " + order.getDirection().name())
					.collect(Collectors.joining(", "));
			dataSql.append(orderByClause);
		}

		Query query = em.createNativeQuery(dataSql.toString());
		params.forEach(query::setParameter);
		query.setFirstResult((int) pageable.getOffset());
		query.setMaxResults(pageable.getPageSize());

		@SuppressWarnings("unchecked")
		List<Object[]> results = query.getResultList();

		// SELECT 컬럼 순서: =championCombination, [1]=frequency, [2]=winCount,
		// [3]=latestGameDate, [4]=latestPatch
		List<CombinationStatDto> content = results.stream()
				.map(row -> {
					String combo = (String) row[0];
					long frequency = ((Number) row[1]).longValue();
					long winCount = ((Number) row[2]).longValue();

					LocalDateTime latest = null;
					Object col = row[3];

					if (col != null) {
						if (col instanceof LocalDateTime ldt) {
							latest = ldt;
						} else if (col instanceof java.sql.Timestamp ts) {
							latest = ts.toLocalDateTime();
						} else if (col instanceof Number num) { // SQLite에서 epoch ms인 경우
							latest = LocalDateTime.ofInstant(
									Instant.ofEpochMilli(num.longValue()),
									ZoneId.of("Asia/Seoul"));
						}
					}

					String latestPatch = (String) row[4];
					return new CombinationStatDto(combo, frequency, winCount, latest, latestPatch);
				})
				.toList();

		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public List<Long> findGameIdsByCombination(List<String> championNames, MultiCombinationFilterDto filter) {
		Map<String, Object> params = new HashMap<>();
		String baseQuery = buildBaseQuery(championNames, filter, params);

		String sql = baseQuery + " SELECT DISTINCT gameId FROM team_combinations";
		Query query = em.createNativeQuery(sql, Long.class);
		params.forEach(query::setParameter);

		@SuppressWarnings("unchecked")
		List<Long> ids = query.getResultList();
		return ids;
	}

	@Override
	public Optional<CombinationStatDto> findSingleCombinationStat(List<String> championNames,
			MultiCombinationFilterDto filter) {
		Map<String, Object> params = new HashMap<>();
		String baseQuery = buildBaseQuery(championNames, filter, params);

		StringBuilder dataSql = new StringBuilder(baseQuery);
		dataSql.append("""
				, annotated AS (
				    SELECT
				        championCombination,
				        isWin,
				        gameDate,
				        patch,
				        ROW_NUMBER() OVER (
				            PARTITION BY championCombination
				            ORDER BY gameDate DESC
				        ) AS rn
				    FROM team_combinations
				)
				SELECT
				    championCombination,
				    COUNT(*) AS frequency,
				    SUM(isWin) AS winCount,
				    MAX(gameDate) AS latestGameDate,
				    MAX(CASE WHEN rn = 1 THEN patch END) AS latestPatch
				FROM annotated
				GROUP BY championCombination
				""");

		String championCombinationStr = championNames.stream().sorted().collect(Collectors.joining(","));

		dataSql.append(" HAVING championCombination = :championCombinationStr");
		params.put("championCombinationStr", championCombinationStr);

		Query query = em.createNativeQuery(dataSql.toString());
		params.forEach(query::setParameter);

		@SuppressWarnings("unchecked")
		List<Object[]> results = query.getResultList();
		if (results.isEmpty())
			return Optional.empty();

		Object[] row = results.get(0);
		// 수정 1: 인덱스 0으로 접근
		String combo = (String) row[0];
		// 수정 2: SELECT 문에 맞는 올바른 인덱스 사용
		long frequency = ((Number) row[1]).longValue();
		long winCount = ((Number) row[2]).longValue();
		java.time.LocalDateTime latest = null;
		Object col = row[3];

		if (col != null) {
			if (col instanceof java.time.LocalDateTime ldt) {
				latest = ldt;
			} else if (col instanceof java.sql.Timestamp ts) {
				latest = ts.toLocalDateTime();
			} else if (col instanceof Number num) {
				latest = LocalDateTime.ofInstant(
						Instant.ofEpochMilli(num.longValue()),
						ZoneId.of("Asia/Seoul"));
			}
		}
		String latestPatch = (String) row[4];

		CombinationStatDto dto = new CombinationStatDto(combo, frequency, winCount, latest, latestPatch);
		return Optional.of(dto);

	}

	// --- MySQL CTE 빌더 ---
	private String buildBaseQuery(List<String> championNames, MultiCombinationFilterDto filter,
			Map<String, Object> params) {
		StringBuilder sb = new StringBuilder();

		sb.append("""
				WITH valid_teams AS (
				    SELECT gp.game_id, gp.team_id
				    FROM game_participants gp
				    JOIN champions c ON gp.champion_id = c.champion_id
				    WHERE c.champion_name_en IN (:championNames)
				    GROUP BY gp.game_id, gp.team_id
				    HAVING COUNT(DISTINCT c.champion_name_en) = :championCount
				),
				team_combinations AS (
				    SELECT
				        /* 정렬된 하위 SELECT → group_concat로 조합 문자열 생성 (MySQL Syntax) */
				        (
				          SELECT group_concat(x.champion_name_en SEPARATOR ',')
				          FROM (
				            SELECT c2.champion_name_en
				            FROM game_participants p2
				            JOIN champions c2 ON p2.champion_id = c2.champion_id
				            WHERE p2.game_id = vt.game_id AND p2.team_id = vt.team_id
				            AND c2.champion_name_en IS NOT NULL AND c2.champion_name_en != ''
				            ORDER BY c2.champion_name_en
				          ) x
				        ) AS championCombination,
				        /* 같은 팀은 동일 승패 → 0/1로 수치화 후 MAX */
				        MAX(CASE WHEN p.is_win THEN 1 ELSE 0 END) AS isWin,
				        g.actual_game_start_time AS gameDate,
				        g.patch AS patch,
				        vt.game_id AS gameId
				    FROM game_participants p
				    JOIN valid_teams vt ON p.game_id = vt.game_id AND p.team_id = vt.team_id
				    JOIN champions c ON p.champion_id = c.champion_id
				    JOIN games g ON p.game_id = g.game_id
				    JOIN teams t ON p.team_id = t.team_id
				    LEFT JOIN leagues l ON g.league_id = l.league_id
				    WHERE 1=1
				""");

		params.put("championNames", championNames);
		params.put("championCount", championNames.size());

		// 동적 필터
		if (filter.getYear() != null) {
			sb.append(" AND l.season_year = :year");
			params.put("year", filter.getYear());
		}
		if (filter.getPatch() != null && !filter.getPatch().isEmpty()) {
			sb.append(" AND g.patch = :patch");
			params.put("patch", filter.getPatch());
		}
		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			sb.append(" AND l.season_split IN (:splits)");
			params.put("splits", filter.getSplits());
		}
		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			sb.append(" AND l.league_name IN (:leagueNames)");
			params.put("leagueNames", filter.getLeagueNames());
		}
		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			sb.append(" AND t.team_name IN (:teamNames)");
			params.put("teamNames", filter.getTeamNames());
		}

		// 팀 단위 1로우 보장
		sb.append(" GROUP BY vt.game_id, vt.team_id, g.actual_game_start_time, g.patch ) ");
		return sb.toString();
	}
}
