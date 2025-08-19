package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.CombinationFilterDto;
import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CombinationIdService {

	// Multi 조합 ID와 검색 컨텍스트를 매핑하는 캐시
	private final Map<String, MultiCombinationSearchContext> multiCombinationCache = new ConcurrentHashMap<>();

	public String createMultiCombinationId(List<String> champions, MultiCombinationFilterDto filter) {
		StringBuilder sb = new StringBuilder();

		// 챔피언 정보
		String championKey = champions.stream()
			.sorted()
			.collect(Collectors.joining("_"));
		sb.append(championKey);

		// 필터 정보
		if (filter.getYear() != null) {
			sb.append("_Y").append(filter.getYear());
		}

		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			String splitKey = filter.getSplits().stream()
				.sorted()
				.collect(Collectors.joining(","));
			sb.append("_S").append(splitKey);
		}

		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			String leagueKey = filter.getLeagueNames().stream()
				.sorted()
				.collect(Collectors.joining(","));
			sb.append("_L").append(leagueKey);
		}

		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			String teamKey = filter.getTeamNames().stream()
				.sorted()
				.collect(Collectors.joining(","));
			sb.append("_T").append(teamKey);
		}

		if (filter.getPatch() != null) {
			sb.append("_P").append(filter.getPatch());
		}

		String id = Base64.getEncoder().encodeToString(sb.toString().getBytes());

		// 캐시에 저장
		MultiCombinationSearchContext context = new MultiCombinationSearchContext(champions, filter);
		multiCombinationCache.put(id, context);

		return id;
	}

	// Multi 검색 컨텍스트 조회 메서드
	public MultiCombinationSearchContext getMultiSearchContext(String combinationId) {
		MultiCombinationSearchContext context = multiCombinationCache.get(combinationId);
		if (context == null) {
			try {
				String decoded = new String(Base64.getDecoder().decode(combinationId));
				log.warn("[WARN] Multi combination ID not found: {} (decoded: {})", combinationId, decoded);
			} catch (Exception e) {
				log.warn("[WARN] Invalid combination ID format: {}", combinationId);
			}
		}
		return context;
	}

	// Multi 검색 컨텍스트 레코드
	public record MultiCombinationSearchContext(
		List<String> champions,
		MultiCombinationFilterDto filter
	) {}
}
