// combination/service/CombinationService.java
package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.converter.CombinationDtoConverter;
import com.toy.nar.app.analysis.dto.PageCombinationResponse;
import com.toy.nar.app.analysis.dto.UpdateInfoDto;
import com.toy.nar.domain.combination.TeamCompositionFactory;
import com.toy.nar.domain.combination.ChampionCombination;
import com.toy.nar.domain.combination.GameTeamKey;
import com.toy.nar.domain.combination.TeamComposition;
import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.app.analysis.dto.CombinationResponseDto;
import com.toy.nar.domain.combination.strategy.CombinationFilterManager;
import com.toy.nar.domain.combination.strategy.MultiCombinationFilterDto;
import com.toy.nar.common.NameNormalizer;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.toy.nar.app.analysis.dto.CombinationFilterDto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CombinationService {

	private final CombinationAnalyzer analyzer;
	private final TeamCompositionFactory factory;
	private final CombinationDtoConverter converter;
	private final GameParticipantRepository gameParticipantRepository;
	private final CombinationIdService idService;
	private final CombinationFilterManager filterManager;

	private LocalDateTime lastUpdateTime = LocalDateTime.now();

	@Transactional
	public void updateInfo() {
		lastUpdateTime = LocalDateTime.now();
	}

	public UpdateInfoDto getUpdateInfo() {
		return new UpdateInfoDto(lastUpdateTime);
	}

	public PageCombinationResponse findTopCombinationsV2(
		List<String> championNames,
		MultiCombinationFilterDto filter,
		Pageable pageable) {

		List<GameParticipant> allParticipants = getFilteredParticipants(championNames, filter);
		long totalCount = allParticipants.size();


		if (allParticipants.isEmpty()) {
			return new PageCombinationResponse(Collections.emptyList(), pageable, false, totalCount);
		}

		List<TeamComposition> allCompositions = convertToCompositions(allParticipants);
		List<ChampionCombination> allCombinations = analyzer.findTopCombinations(allCompositions, championNames);

		// 동적 정렬 적용 (기존 getComparator 사용)
		String sortType = pageable.getSort().stream()
			.findFirst()
			.map(Sort.Order::getProperty)
			.orElse("frequency");
		log.debug("Applied sortType: {}", sortType);

		Comparator<ChampionCombination> comparator = getComparator(sortType);
		List<ChampionCombination> sortedCombinations = allCombinations.stream()
			.sorted(comparator)
			.toList();

		// 이제 combinations 리스트를 pageable로 슬라이스
		int from = (int) pageable.getOffset();  // page * size
		int to = Math.min(from + pageable.getPageSize(), sortedCombinations.size());
		List<ChampionCombination> pagedCombinations = sortedCombinations.subList(from, to);

		// ID 생성 및 DTO 변환
		List<CombinationResponseDto> response = IntStream.range(0, pagedCombinations.size())
			.mapToObj(i -> {
				ChampionCombination combination = pagedCombinations.get(i);
				String combinationId = idService.createMultiCombinationId(combination.getChampions(), filter);
				return converter.toResponseDto(combination, from + i + 1, combinationId);  // rank는 전체 순위 기반
			})
			.toList();

		boolean hasNext = to < sortedCombinations.size();
		return new PageCombinationResponse(response, pageable, hasNext, (long) sortedCombinations.size());  // totalCount를 실제 조합 수로 변경
	}

	private Comparator<ChampionCombination> getComparator(String sortType) {
		return switch (sortType != null ? sortType.toLowerCase() : "frequency") {  // 빈도수 디폴트
			case "frequency" -> ChampionCombination::compareByFrequency;
			case "recency" -> ChampionCombination::compareByRecency;
			case "patch" -> ChampionCombination::compareByPatch;
			default -> ChampionCombination::compareByFrequency;  // 디폴트 빈도수
		};
	}

	public Pageable applyDynamicSort(Pageable pageable, String sortType) {
		Sort.Direction direction = Sort.Direction.DESC;
		Sort sort = Sort.by(direction, sortType != null ? sortType.toLowerCase() : "frequency");
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

	// 새로운 메서드: ID로 상세정보 조회
	public CombinationDetailDto getCombinationDetailById(String combinationId) {
		// 먼저 Multi 캐시에서 확인
		CombinationIdService.MultiCombinationSearchContext multiContext =
			idService.getMultiSearchContext(combinationId);

		if (multiContext != null) {
			log.debug("[DEBUG] Retrieving multi combination detail for ID: {}", combinationId);
			return getCombinationDetailMulti(multiContext.champions(), multiContext.filter());
		}

		// 기존 캐시에서 확인
		CombinationIdService.CombinationSearchContext context =
			idService.getSearchContext(combinationId);

		if (context != null) {
			log.debug("[DEBUG] Retrieving combination detail for ID: {}", combinationId);
			return getCombinationDetail(context.champions(), context.filter());
		}

		throw new IllegalArgumentException("Invalid combination ID: " + combinationId);
	}

	public CombinationDetailDto getCombinationDetailMulti(
		List<String> championNames,
		MultiCombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.toList();

		DetailData detailData = retrieveCombinationAndGameDetails(normalizedChampionNames, filter, true);

		List<String> teamNames = filter.getTeamNames();

		return converter.toDetailDtoMulti(detailData.combinations.get(0), detailData.gameDetails, teamNames, championNames);
	}

	public CombinationDetailDto getCombinationDetail(
		List<String> championNames,
		CombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.toList();

		DetailData detailData = retrieveCombinationAndGameDetails(normalizedChampionNames, filter, false);

		return converter.toDetailDto(detailData.combinations.get(0), detailData.gameDetails, filter.teamName());
	}

	private DetailData retrieveCombinationAndGameDetails(
		List<String> normalizedChampionNames,
		Object filter,
		boolean isMulti) {

		List<GameParticipant> participants = isMulti
			? getFilteredParticipants(normalizedChampionNames, (MultiCombinationFilterDto) filter)
			: gameParticipantRepository.findFilteredParticipants(
			normalizedChampionNames,
			((CombinationFilterDto) filter).year(),
			((CombinationFilterDto) filter).split(),
			((CombinationFilterDto) filter).leagueName(),
			((CombinationFilterDto) filter).teamName(),
			((CombinationFilterDto) filter).patch()
		);

		if (participants.isEmpty()) {
			throw new IllegalArgumentException("No combination found for: " + normalizedChampionNames);
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, normalizedChampionNames);

		if (combinations.isEmpty()) {
			throw new IllegalArgumentException("No valid combinations found for: " + normalizedChampionNames);
		}

		Set<Long> allGameIds = combinations.stream()
			.flatMap(c -> c.getGameIds().stream())
			.collect(Collectors.toSet());

		List<GameParticipant> gameDetails = gameParticipantRepository
			.findGameDetailsByGameIds(allGameIds);

		return new DetailData(combinations, gameDetails);
	}

	private List<TeamComposition> convertToCompositions(List<GameParticipant> participants) {
		return participants.stream()
			.collect(Collectors.groupingBy(
				p -> new GameTeamKey(p.getGame().getId(), p.getTeam().getName())
			))
			.values().stream()
			.map(factory::createFromParticipants)
			.toList();
	}

	private List<GameParticipant> getFilteredParticipants(List<String> championNames,
		MultiCombinationFilterDto filter) {

		// 빈 리스트를 null로 변환하여 쿼리 단순화
		List<String> splits = (filter.getSplits() != null && filter.getSplits().isEmpty())
			? null : filter.getSplits();
		List<String> leagueNames = (filter.getLeagueNames() != null && filter.getLeagueNames().isEmpty())
			? null : filter.getLeagueNames();
		List<String> teamNames = (filter.getTeamNames() != null && filter.getTeamNames().isEmpty())
			? null : filter.getTeamNames();

		if (filterManager.shouldUseMemoryFiltering(filter)) {
			log.debug("[MEMORY] Using memory filtering for complex filters");
			List<GameParticipant> baseParticipants = gameParticipantRepository.findBaseParticipants(
				championNames, filter.getYear(), filter.getPatch());
			return filterManager.applyFilters(baseParticipants, filter);
		}

		log.debug("[DB] Using database filtering");
		return gameParticipantRepository.findFilteredParticipantsMulti(
			championNames,
			filter.getYear(),
			splits,
			leagueNames,
			teamNames,
			filter.getPatch()
		);
	}

	private record DetailData(List<ChampionCombination> combinations, List<GameParticipant> gameDetails) {}
}
