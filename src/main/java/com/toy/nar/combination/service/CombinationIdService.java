package com.toy.nar.combination.service;

import com.toy.nar.combination.dto.CombinationFilterDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CombinationIdService {

	// 조합 ID와 검색 컨텍스트를 매핑하는 캐시
	private final Map<String, CombinationSearchContext> combinationCache = new ConcurrentHashMap<>();

	public String createCombinationId(List<String> champions, CombinationFilterDto filter) {
		// 🔥 챔피언 목록을 정렬하여 일관된 ID 생성
		String sortedChampions = champions.stream()
			.sorted()
			.collect(Collectors.joining(","));

		// 🔥 필터 정보를 포함한 컨텍스트 문자열 생성
		String filterString = String.format("%s_%s_%s_%s_%s",
			filter.year(),
			filter.split(),
			filter.leagueName(),
			filter.teamName(),
			filter.patch());

		// 🔥 해시 기반 조합 ID 생성
		String combinationId = DigestUtils.md5DigestAsHex(
			(sortedChampions + "_" + filterString).getBytes()
		).substring(0, 12); // 12자리로 축약

		// 🔥 검색 컨텍스트 저장
		CombinationSearchContext context = new CombinationSearchContext(champions, filter);
		combinationCache.put(combinationId, context);

		log.debug("🔑 Created combination ID: {} for champions: {}", combinationId, champions);
		return combinationId;
	}

	public CombinationSearchContext getSearchContext(String combinationId) {
		CombinationSearchContext context = combinationCache.get(combinationId);
		if (context == null) {
			log.warn("❌ Combination ID not found: {}", combinationId);
		}
		return context;
	}

	public void clearCache() {
		combinationCache.clear();
		log.info("🗑️ Combination cache cleared");
	}

	// 🔥 검색 컨텍스트 레코드
	public record CombinationSearchContext(
		List<String> champions,
		CombinationFilterDto filter
	) {}
}
