package com.toy.nar.domain.game.repository;

import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.domain.game.entity.Game;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GameRepositoryCustomImpl implements GameRepositoryCustom {

	private final EntityManager em;

	@Override
	public Page<Game> findGamesByFilter(MultiCombinationFilterDto filter, Pageable pageable) {
		// 데이터 조회를 위한 JPQL
		StringBuilder jpql = new StringBuilder("SELECT DISTINCT g FROM Game g JOIN FETCH g.league l");
		if (!CollectionUtils.isEmpty(filter.getTeamNames())) {
			jpql.append(" JOIN g.participants p JOIN p.team t");
		}
		jpql.append(" WHERE 1=1");

		Map<String, Object> parameters = new HashMap<>();
		applyFilterConditions(jpql, parameters, filter);

		String sortOrder = pageable.getSort().stream()
			.map(order -> "g." + order.getProperty() + " " + order.getDirection().name())
			.collect(Collectors.joining(", "));

		if (!sortOrder.isEmpty()) {
			jpql.append(" ORDER BY ").append(sortOrder);
		}

		TypedQuery<Game> query = em.createQuery(jpql.toString(), Game.class);
		parameters.forEach(query::setParameter);

		// 페이지네이션을 위한 전체 카운트 조회
		long total = countQuery(filter);

		query.setFirstResult((int) pageable.getOffset());
		query.setMaxResults(pageable.getPageSize());
		List<Game> games = query.getResultList();

		return new PageImpl<>(games, pageable, total);
	}

	private long countQuery(MultiCombinationFilterDto filter) {
		StringBuilder jpql = new StringBuilder("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.league l");
		if (!CollectionUtils.isEmpty(filter.getTeamNames())) {
			jpql.append(" JOIN g.participants p JOIN p.team t");
		}
		jpql.append(" WHERE 1=1");
		Map<String, Object> parameters = new HashMap<>();
		applyFilterConditions(jpql, parameters, filter);

		TypedQuery<Long> countQuery = em.createQuery(jpql.toString(), Long.class);
		parameters.forEach(countQuery::setParameter);
		return countQuery.getSingleResult();
	}

	private void applyFilterConditions(StringBuilder jpql, Map<String, Object> parameters, MultiCombinationFilterDto filter) {
		if (!CollectionUtils.isEmpty(filter.getLeagueNames())) {
			jpql.append(" AND l.leagueName IN (:leagueNames)");
			parameters.put("leagueNames", filter.getLeagueNames());
		}
		if (!CollectionUtils.isEmpty(filter.getSplits())) {
			jpql.append(" AND l.seasonSplit IN (:splits)");
			parameters.put("splits", filter.getSplits());
		}
		if (!CollectionUtils.isEmpty(filter.getTeamNames())) {
			jpql.append(" AND t.name IN (:teamNames)");
			parameters.put("teamNames", filter.getTeamNames());
		}
	}
}
