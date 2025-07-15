package com.toy.nar.repo;
import com.toy.nar.dto.CombinationFilterDto;
import com.toy.nar.dto.CombinationStatDto;
import com.toy.nar.entity.GameParticipant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CombinationQueryRepository {

	private final EntityManager em;

	// 1단계 쿼리: 동적 쿼리 (필터 조건에 따라 WHERE 절이 바뀜)
	public List<Long> findGameIdsByCriteria(List<String> championNames, CombinationFilterDto filter) {
		// 기본 JPQL 쿼리
		StringBuilder jpql = new StringBuilder(
			"SELECT p.game.id FROM GameParticipant p JOIN p.champion c " +
				"WHERE c.championNameEn IN (:championNames) "
		);
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("championNames", championNames);
		if (filter.year() != null || filter.split() != null || filter.leagueName() != null) {
			// 이 필터들은 league 정보가 필요하므로, JOIN 절을 추가합니다.
			jpql.append("AND p.game.id IN (SELECT g.id FROM Game g JOIN g.league l WHERE 1=1 ");

			if (filter.year() != null) {
				jpql.append("AND l.seasonYear = :year ");
				parameters.put("year", filter.year());
			}
			if (filter.split() != null) {
				jpql.append("AND l.seasonSplit = :split ");
				parameters.put("split", filter.split());
			}
			if (filter.leagueName() != null) {
				jpql.append("AND l.leagueName = :leagueName ");
				parameters.put("leagueName", filter.leagueName());
			}
			jpql.append(") "); // 서브쿼리 닫기
		}
		if (filter.teamName() != null) {
			jpql.append("AND p.game.id IN (SELECT p2.game.id FROM GameParticipant p2 JOIN p2.team t2 WHERE t2.name = :teamName) ");
			parameters.put("teamName", filter.teamName());
		}

		jpql.append("GROUP BY p.game.id HAVING COUNT(DISTINCT c.id) = :championCount");
		parameters.put("championCount", (long) championNames.size());

		Query query = em.createQuery(jpql.toString());
		// 4. 맵에 담아둔 파라미터를 한 번에 바인딩합니다.
		parameters.forEach(query::setParameter);

		return query.getResultList();
	}

	public List<CombinationStatDto> findCombinationsContainingChampion(List<Long> gameIds, String championName) {
		String jpql = """
        SELECT gp FROM GameParticipant gp 
        JOIN FETCH gp.game g 
        JOIN FETCH gp.champion c 
        WHERE g.id IN (:gameIds)
        """;

		List<GameParticipant> participants = em.createQuery(jpql, GameParticipant.class)
			.setParameter("gameIds", gameIds)
			.getResultList();

		// 게임별 그룹화
		Map<Long, List<GameParticipant>> gameGroups = participants.stream()
			.collect(Collectors.groupingBy(gp -> gp.getGame().getId()));

		// 조합 빈도 계산
		Map<List<String>, Long> combinationCounts = new HashMap<>();

		for (Map.Entry<Long, List<GameParticipant>> entry : gameGroups.entrySet()) {
			List<GameParticipant> gameParticipants = entry.getValue();

			// 5인 조합만 처리
			if (gameParticipants.size() != 5) continue;

			List<String> combination = gameParticipants.stream()
				.map(gp -> gp.getChampion().getChampionNameEn())
				.sorted()
				.collect(Collectors.toList());

			// 특정 챔피언이 포함된 조합만 처리
			if (combination.contains(championName)) {
				combinationCounts.merge(combination, 1L, Long::sum);
			}
		}

		// 결과 반환
		return combinationCounts.entrySet().stream()
			.sorted(Map.Entry.<List<String>, Long>comparingByValue().reversed())
			.limit(10)
			.map(entry -> new CombinationStatDto(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	// 2단계 쿼리: Native Query (GROUP_CONCAT은 JPQL 표준이 아니므로 Native Query 사용)
	public List<Object[]> findTopCombinationsByGameIds(List<Long> gameIds) {
		String sql = "SELECT " +
			"    GROUP_CONCAT(c.champion_name_en ORDER BY c.champion_name_en SEPARATOR ',') as combination, " +
			"    COUNT(*) as frequency " +
			"FROM game_participants gp " +
			"JOIN champions c ON gp.champion_id = c.champion_id " +
			"WHERE gp.game_id IN (:gameIds) " +
			"GROUP BY gp.game_id " +
			"HAVING COUNT(gp.participant_game_id) = 5 " + // 5명 조합만 필터링
			"ORDER BY frequency DESC, combination ASC " +
			"LIMIT 10"; // 상위 10개 조합만

		Query query = em.createNativeQuery(sql);
		query.setParameter("gameIds", gameIds);

		return query.getResultList();
	}
}
