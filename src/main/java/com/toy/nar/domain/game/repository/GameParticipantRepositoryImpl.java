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
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GameParticipantRepositoryImpl implements GameParticipantRepositoryCustom {

	private final EntityManager em;

	@Override
	public Page<CombinationStatDto> findCombinationStats(List<String> championNames, MultiCombinationFilterDto filter, Pageable pageable) {

		Map<String, Object> params = new HashMap<>();

		// 1. CTE(WITH절)를 포함한 쿼리의 기본 뼈대
		StringBuilder baseQuery = new StringBuilder();
		baseQuery.append("""
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
                    GROUP_CONCAT(c.champion_name_en ORDER BY c.champion_name_en SEPARATOR ',') as championCombination,
                    p.is_win as isWin,
                    g.actual_game_start_time as gameDate,
                    g.patch as patch
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

		// 2. 동적 필터 조건 추가
		if (filter.getYear() != null) {
			baseQuery.append(" AND l.season_year = :year");
			params.put("year", filter.getYear());
		}
		if (filter.getPatch() != null && !filter.getPatch().isEmpty()) {
			baseQuery.append(" AND g.patch = :patch");
			params.put("patch", filter.getPatch());
		}
		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			baseQuery.append(" AND l.season_split IN (:splits)");
			params.put("splits", filter.getSplits());
		}
		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			baseQuery.append(" AND l.league_name IN (:leagueNames)");
			params.put("leagueNames", filter.getLeagueNames());
		}
		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			baseQuery.append(" AND t.name IN (:teamNames)");
			params.put("teamNames", filter.getTeamNames());
		}

		baseQuery.append(" GROUP BY vt.game_id, vt.team_id, p.is_win, g.actual_game_start_time, g.patch )");

		// 3. Count 쿼리 실행 (단순하고 확실한 방식)
		String countSql = baseQuery.toString() + " SELECT count(*) FROM (SELECT DISTINCT championCombination FROM team_combinations) as distinct_combinations";
		Query countQuery = em.createNativeQuery(countSql);
		params.forEach(countQuery::setParameter);
		long total = ((Number) countQuery.getSingleResult()).longValue();

		// 4. 데이터 조회 쿼리 실행 (최종 집계)
		StringBuilder dataSql = new StringBuilder(baseQuery.toString());
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
				((BigDecimal) row[2]).longValue(), // SUM(boolean) can return BigDecimal
				(row[3] instanceof Timestamp) ? ((Timestamp) row[3]).toLocalDateTime() : null,
				(String) row[4]
			))
			.toList();

		return new PageImpl<>(content, pageable, total);
	}
}