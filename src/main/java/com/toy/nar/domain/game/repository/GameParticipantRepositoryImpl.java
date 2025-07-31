package com.toy.nar.domain.game.repository;

import com.toy.nar.app.analysis.dto.CombinationStatDto;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GameParticipantRepositoryImpl implements GameParticipantRepositoryCustom {

	private final EntityManager em;

	@Override
	public Page<CombinationStatDto> findCombinationStats(List<String> championNames, MultiCombinationFilterDto filter, Pageable pageable) {

		Map<String, Object> params = new HashMap<>();

		// 1. CTE(WITH절)를 포함한 쿼리의 기본 뼈대 구성
		String baseQuery = buildBaseQuery(championNames, filter, params);

		// 2. Count 쿼리 실행
		String countSql = baseQuery + " SELECT COUNT(*) FROM (SELECT 1 FROM team_combinations GROUP BY championCombination) as distinct_combinations";
		Query countQuery = em.createNativeQuery(countSql);
		params.forEach(countQuery::setParameter);
		long total = ((Number) countQuery.getSingleResult()).longValue();

		// 3. 데이터 조회 쿼리 (최종 집계)
		StringBuilder dataSql = new StringBuilder(baseQuery);
		dataSql.append("""
            SELECT
                championCombination,
                COUNT(*) as frequency,
                SUM(isWin) as winCount,
                MAX(gameDate) as latestGameDate,
                SUBSTRING_INDEX(GROUP_CONCAT(patch ORDER BY gameDate DESC SEPARATOR ','), ',', 1) as latestPatch
            FROM team_combinations
            GROUP BY championCombination
            """);

		// 정렬 조건 추가
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

		List<Object[]> results = query.getResultList();

		List<CombinationStatDto> content = results.stream()
			.map(row -> new CombinationStatDto(
				(String) row[0],
				((Number) row[1]).longValue(),
				((BigDecimal) row[2]).longValue(), // SUM(boolean) can return BigDecimal in MySQL
				(row[3] instanceof Timestamp) ? ((Timestamp) row[3]).toLocalDateTime() : null,
				(String) row[4]
			))
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

		return query.getResultList();
	}

	@Override
	public Optional<CombinationStatDto> findSingleCombinationStat(List<String> championNames, MultiCombinationFilterDto filter) {
		Map<String, Object> params = new HashMap<>();
		String baseQuery = buildBaseQuery(championNames, filter, params);

		StringBuilder dataSql = new StringBuilder(baseQuery);
		dataSql.append("""
            SELECT
                championCombination,
                COUNT(*) as frequency,
                SUM(isWin) as winCount,
                MAX(gameDate) as latestGameDate,
                SUBSTRING_INDEX(GROUP_CONCAT(patch ORDER BY gameDate DESC SEPARATOR ','), ',', 1) as latestPatch
            FROM team_combinations
            GROUP BY championCombination
            """);

		String championCombinationStr = championNames.stream().sorted().collect(Collectors.joining(","));
		dataSql.append(" HAVING championCombination = :championCombinationStr");
		params.put("championCombinationStr", championCombinationStr);

		Query query = em.createNativeQuery(dataSql.toString());
		params.forEach(query::setParameter);

		List<Object[]> results = query.getResultList();
		if (results.isEmpty()) {
			return Optional.empty();
		}

		Object[] row = results.get(0);
		CombinationStatDto dto = new CombinationStatDto(
			(String) row[0],
			((Number) row[1]).longValue(),
			((BigDecimal) row[2]).longValue(),
			(row[3] instanceof Timestamp) ? ((Timestamp) row[3]).toLocalDateTime() : null,
			(String) row[4]
		);
		return Optional.of(dto);
	}

	// --- Private Helper Method to build the common query part ---

	private String buildBaseQuery(List<String> championNames, MultiCombinationFilterDto filter, Map<String, Object> params) {
		StringBuilder queryBuilder = new StringBuilder();
		// CTE Part 1: 조건에 맞는 팀(game_id, team_id) 목록을 찾습니다.
		queryBuilder.append("""
            WITH valid_teams AS (
                SELECT gp.game_id, gp.team_id
                FROM game_participants gp
                JOIN champions c ON gp.champion_id = c.champion_id
                WHERE c.champion_name_en IN (:championNames)
                GROUP BY gp.game_id, gp.team_id
                HAVING COUNT(DISTINCT c.champion_name_en) = :championCount
            ),
            -- CTE Part 2: 찾은 팀들의 상세 정보(챔피언 조합 문자열, 승패 등)를 만듭니다.
            team_combinations AS (
                SELECT
                    GROUP_CONCAT(c.champion_name_en ORDER BY c.champion_name_en SEPARATOR ',') as championCombination,
                    p.is_win as isWin,
                    g.actual_game_start_time as gameDate,
                    g.patch as patch,
                    vt.game_id as gameId
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

		// Dynamic Filtering Part
		if (filter.getYear() != null) {
			queryBuilder.append(" AND l.season_year = :year");
			params.put("year", filter.getYear());
		}
		if (filter.getPatch() != null && !filter.getPatch().isEmpty()) {
			queryBuilder.append(" AND g.patch = :patch");
			params.put("patch", filter.getPatch());
		}
		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			queryBuilder.append(" AND l.season_split IN (:splits)");
			params.put("splits", filter.getSplits());
		}
		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			queryBuilder.append(" AND l.league_name IN (:leagueNames)");
			params.put("leagueNames", filter.getLeagueNames());
		}
		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			queryBuilder.append(" AND t.name IN (:teamNames)");
			params.put("teamNames", filter.getTeamNames());
		}

		queryBuilder.append(" GROUP BY vt.game_id, vt.team_id, p.is_win, g.actual_game_start_time, g.patch ) ");

		return queryBuilder.toString();
	}
}