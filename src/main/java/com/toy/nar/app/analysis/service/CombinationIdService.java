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

		// 필터 정보 (구분자를 | 로 변경하여 파싱 용이성 확보)
		if (filter.getYear() != null) {
			sb.append("|Y").append(filter.getYear());
		}

		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			String splitKey = filter.getSplits().stream()
					.sorted()
					.collect(Collectors.joining(","));
			sb.append("|S").append(splitKey);
		}

		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			String leagueKey = filter.getLeagueNames().stream()
					.sorted()
					.collect(Collectors.joining(","));
			sb.append("|L").append(leagueKey);
		}

		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			String teamKey = filter.getTeamNames().stream()
					.sorted()
					.collect(Collectors.joining(","));
			sb.append("|T").append(teamKey);
		}

		if (filter.getPatch() != null) {
			sb.append("|P").append(filter.getPatch());
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
				// 캐시에 없으면 파싱 시도 (Stateless 지원)
				context = parseAndCache(combinationId, decoded);
			} catch (Exception e) {
				log.warn("[WARN] Invalid combination ID format or decode failed: {}", combinationId);
			}
		}
		return context;
	}

	private MultiCombinationSearchContext parseAndCache(String id, String decoded) {
		// 구분자 | 로 분리 (없으면 전체가 챔피언 키)
		String[] parts = decoded.split("\\|");

		// 첫 번째 파트는 무조건 챔피언 목록 ( _ 로 연결됨 )
		List<String> champions = List.of(parts[0].split("_"));

		MultiCombinationFilterDto.MultiCombinationFilterDtoBuilder filterBuilder = MultiCombinationFilterDto.builder();

		// 나머지 파트는 필터
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i];
			if (part.startsWith("Y")) {
				filterBuilder.year(Integer.parseInt(part.substring(1)));
			} else if (part.startsWith("S")) {
				filterBuilder.splits(List.of(part.substring(1).split(",")));
			} else if (part.startsWith("L")) {
				filterBuilder.leagueNames(List.of(part.substring(1).split(",")));
			} else if (part.startsWith("T")) {
				filterBuilder.teamNames(List.of(part.substring(1).split(",")));
			} else if (part.startsWith("P")) {
				filterBuilder.patch(part.substring(1));
			}
		}

		MultiCombinationSearchContext context = new MultiCombinationSearchContext(champions, filterBuilder.build());
		multiCombinationCache.put(id, context);
		log.info("Restored combination context from ID: {}", id);
		return context;
	}

	// Multi 검색 컨텍스트 레코드
	public record MultiCombinationSearchContext(
			List<String> champions,
			MultiCombinationFilterDto filter) {
	}
}
