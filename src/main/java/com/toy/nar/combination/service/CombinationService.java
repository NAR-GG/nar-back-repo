// combination/service/CombinationService.java
package com.toy.nar.combination.service;

import com.toy.nar.combination.converter.CombinationDtoConverter;
import com.toy.nar.combination.converter.TeamCompositionFactory;
import com.toy.nar.combination.domain.ChampionCombination;
import com.toy.nar.combination.domain.GameTeamKey;
import com.toy.nar.combination.domain.TeamComposition;
import com.toy.nar.combination.dto.CombinationDetailDto;
import com.toy.nar.combination.dto.CombinationResponseDto;
import com.toy.nar.combination.dto.CombinationStatDto;
import com.toy.nar.combination.strategy.CombinationFilterManager;
import com.toy.nar.combination.strategy.MultiCombinationFilterDto;
import com.toy.nar.common.NameNormalizer;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.repository.GameParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.toy.nar.combination.dto.CombinationFilterDto;

import java.util.Collections;
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


	public List<CombinationResponseDto> findTopCombinationsV2(
		List<String> championNames,
		MultiCombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		List<GameParticipant> participants = getFilteredParticipants(normalizedChampionNames, filter);

		if (participants.isEmpty()) {
			log.warn("⚠️ No participants found for filter: {}", filter);
			return Collections.emptyList();
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, championNames);

		List<ChampionCombination> sortedCombinations = combinations.stream()
			.sorted(ChampionCombination::compareByRecency)
			.limit(10)
			.collect(Collectors.toList());

		// 각 조합에 대해 고유 ID 생성 및 저장
		return IntStream.range(0, sortedCombinations.size())
			.mapToObj(i -> {
				ChampionCombination combination = sortedCombinations.get(i);
				String combinationId = idService.createMultiCombinationId(combination.getChampions(), filter);
				return converter.toResponseDto(combination, i + 1, combinationId);
			})
			.collect(Collectors.toList());
	}

	// 조합 ID를 포함한 응답 반환
	public List<CombinationResponseDto> findTopCombinations(
		List<String> championNames,
		CombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		List<GameParticipant> participants = gameParticipantRepository.findFilteredParticipants(
			normalizedChampionNames,
			filter.year(),
			filter.split(),
			filter.leagueName(),
			filter.teamName(),
			filter.patch()
		);

		if (participants.isEmpty()) {
			log.warn("⚠️ No participants found");
			return Collections.emptyList();
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, championNames);

		List<ChampionCombination> sortedCombinations = combinations.stream()
			.sorted(ChampionCombination::compareByRecency)
			.limit(10)
			.collect(Collectors.toList());

		// 각 조합에 대해 고유 ID 생성 및 저장
		return IntStream.range(0, sortedCombinations.size())
			.mapToObj(i -> {
				ChampionCombination combination = sortedCombinations.get(i);

				// 조합 ID 생성 및 저장
				String combinationId = idService.createCombinationId(
					combination.getChampions(), filter);

				return converter.toResponseDto(combination, i + 1, combinationId);
			})
			.collect(Collectors.toList());
	}

	// 새로운 메서드: ID로 상세정보 조회
	public CombinationDetailDto getCombinationDetailById(String combinationId) {
		// 먼저 Multi 캐시에서 확인
		CombinationIdService.MultiCombinationSearchContext multiContext =
			idService.getMultiSearchContext(combinationId);

		if (multiContext != null) {
			log.info("🔍 Retrieving multi combination detail for ID: {}", combinationId);
			return getCombinationDetailMulti(multiContext.champions(), multiContext.filter());
		}

		// 기존 캐시에서 확인
		CombinationIdService.CombinationSearchContext context =
			idService.getSearchContext(combinationId);

		if (context != null) {
			log.info("🔍 Retrieving combination detail for ID: {}", combinationId);
			return getCombinationDetail(context.champions(), context.filter());
		}

		throw new IllegalArgumentException("Invalid combination ID: " + combinationId);
	}

	// 새로운 메서드: Multi 필터 상세정보 조회
	public CombinationDetailDto getCombinationDetailMulti(
		List<String> championNames,
		MultiCombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		List<GameParticipant> participants = getFilteredParticipants(normalizedChampionNames, filter);

		if (participants.isEmpty()) {
			throw new IllegalArgumentException("No combination found for: " + championNames);
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, championNames);

		if (combinations.isEmpty()) {
			throw new IllegalArgumentException("No valid combinations found for: " + championNames);
		}

		Set<Long> allGameIds = combinations.stream()
			.flatMap(c -> c.getGameIds().stream())
			.collect(Collectors.toSet());

		List<GameParticipant> gameDetails = gameParticipantRepository
			.findGameDetailsByGameIds(allGameIds);

		// 🔥 다중 팀 리스트 전달
		List<String> teamNames = filter.getTeamNames();

		return converter.toDetailDtoMulti(combinations.get(0), gameDetails, teamNames, championNames);
	}

	// 기존 메서드 유지 (내부 사용)
	public CombinationDetailDto getCombinationDetail(
		List<String> championNames,
		CombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		List<GameParticipant> participants = gameParticipantRepository.findFilteredParticipants(
			normalizedChampionNames,
			filter.year(),
			filter.split(),
			filter.leagueName(),
			filter.teamName(),
			filter.patch()
		);

		if (participants.isEmpty()) {
			throw new IllegalArgumentException("No combination found for: " + championNames);
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, championNames);

		if (combinations.isEmpty()) {
			throw new IllegalArgumentException("No valid combinations found for: " + championNames);
		}

		Set<Long> allGameIds = combinations.stream()
			.flatMap(c -> c.getGameIds().stream())
			.collect(Collectors.toSet());

		List<GameParticipant> gameDetails = gameParticipantRepository
			.findGameDetailsByGameIds(allGameIds);

		return converter.toDetailDto(combinations.get(0), gameDetails, filter.teamName());
	}

	// 기존 레거시 메서드 유지
	public List<CombinationStatDto> findTopCombinationsLegacy(
		List<String> championNames,
		CombinationFilterDto filter) {

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		List<GameParticipant> participants = gameParticipantRepository.findFilteredParticipants(
			normalizedChampionNames,
			filter.year(),
			filter.split(),
			filter.leagueName(),
			filter.teamName(),
			filter.patch()
		);

		if (participants.isEmpty()) {
			log.warn("⚠️ No participants found");
			return Collections.emptyList();
		}

		List<TeamComposition> compositions = convertToCompositions(participants);
		List<ChampionCombination> combinations = analyzer.findTopCombinations(compositions, championNames);

		return combinations.stream()
			.map(converter::toStatDto)
			.collect(Collectors.toList());
	}

	private List<TeamComposition> convertToCompositions(List<GameParticipant> participants) {
		return participants.stream()
			.collect(Collectors.groupingBy(
				p -> new GameTeamKey(p.getGame().getId(), p.getTeam().getName())
			))
			.values().stream()
			.map(factory::createFromParticipants)
			.collect(Collectors.toList());
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
			log.info("🔄 Using memory filtering for complex filters");
			List<GameParticipant> baseParticipants = gameParticipantRepository.findBaseParticipants(
				championNames, filter.getYear(), filter.getPatch());
			return filterManager.applyFilters(baseParticipants, filter);
		}

		log.info("🔄 Using database filtering");
		return gameParticipantRepository.findFilteredParticipantsMulti(
			championNames,
			filter.getYear(),
			splits,
			leagueNames,
			teamNames,
			filter.getPatch()
		);
	}
}
