package com.toy.nar.app.analysis.service;

import com.toy.nar.app.analysis.dto.*;
import com.toy.nar.app.analysis.dto.MultiCombinationFilterDto;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.app.analysis.converter.GameDetailConverter; // 상세 DTO 변환을 위한 컨버터
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CombinationService {

	private final GameParticipantRepository gameParticipantRepository;
	private final CombinationIdService idService;
	private final GameDetailConverter gameDetailConverter;

	private LocalDateTime lastUpdateTime = LocalDateTime.now();

	@Transactional
	public void updateInfo() {
		lastUpdateTime = LocalDateTime.now();
	}

	public UpdateInfoDto getUpdateInfo() {
		return new UpdateInfoDto(lastUpdateTime);
	}

	/**
	 * [V3] DB 최적화를 적용한 메인 조합 조회 메서드
	 */
	public PageCombinationResponse findTopCombinationsV3(
		List<String> championNames,
		MultiCombinationFilterDto filter,
		Pageable pageable) {

		Page<CombinationStatDto> statsPage = gameParticipantRepository.findCombinationStats(
			championNames,
			filter,
			pageable
		);

		List<CombinationResponseDto> responseDtos = IntStream.range(0, statsPage.getContent().size())
			.mapToObj(i -> {
				CombinationStatDto stat = statsPage.getContent().get(i);
				long lossCount = stat.getFrequency() - stat.getWinCount();
				double winRate = (stat.getFrequency() > 0)
					? (double) stat.getWinCount() / stat.getFrequency() * 100
					: 0.0;
				int rank = (int) pageable.getOffset() + i + 1;
				String combinationId = idService.createMultiCombinationId(stat.getChampions(), filter);

				return new CombinationResponseDto(
					combinationId,
					rank,
					stat.getChampions(),
					stat.getFrequency(),
					stat.getWinCount(),
					lossCount,
					winRate,
					stat.getLatestGameDate().toLocalDate(),
					stat.getLatestPatch()
				);
			})
			.toList();

		return new PageCombinationResponse(
			responseDtos,
			statsPage.getPageable(),
			statsPage.hasNext(),
			statsPage.getTotalElements()
		);
	}

	/**
	 * 정렬 파라미터를 DB 컬럼명에 맞게 변환하는 헬퍼 메서드
	 */
	public Pageable applyDynamicSort(Pageable pageable, String sortType) {
		String property = switch (sortType.toLowerCase()) {
			case "recency" -> "latestGameDate";
			case "patch" -> "latestPatch";
			default -> "frequency";
		};
		Sort sort = Sort.by(Sort.Direction.DESC, property);
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

	/**
	 * [V3] DB 최적화를 적용한 조합 상세 조회 메서드
	 */
	public CombinationDetailDto getCombinationDetailById(String combinationId) {
		CombinationIdService.MultiCombinationSearchContext context =
			idService.getMultiSearchContext(combinationId);

		if (context == null) {
			throw new IllegalArgumentException("Invalid or expired combination ID: " + combinationId);
		}

		List<String> champions = context.champions();
		MultiCombinationFilterDto filter = context.filter();

		// 1. 해당 조합의 통계 정보(요약)를 가져옵니다.
		CombinationStatDto summaryStat = gameParticipantRepository.findSingleCombinationStat(champions, filter)
			.orElseThrow(() -> new IllegalArgumentException("Combination not found for given context."));

		// CombinationStatDto -> CombinationResponseDto 변환
		CombinationResponseDto summaryDto = new CombinationResponseDto(
			combinationId, 1, summaryStat.getChampions(), summaryStat.getFrequency(),
			summaryStat.getWinCount(), summaryStat.getFrequency() - summaryStat.getWinCount(),
			(summaryStat.getFrequency() > 0) ? (double) summaryStat.getWinCount() / summaryStat.getFrequency() * 100 : 0.0,
			summaryStat.getLatestGameDate().toLocalDate(), summaryStat.getLatestPatch()
		);

		// 2. 해당 조합이 사용된 모든 게임의 ID 목록을 가져옵니다.
		List<Long> gameIds = gameParticipantRepository.findGameIdsByCombination(champions, filter);
		if (gameIds.isEmpty()) {
			// 통계는 있는데 게임 목록이 없는 경우는 거의 없지만, 방어 코드
			return new CombinationDetailDto(summaryDto, List.of());
		}

		// 3. 게임 ID 목록으로 모든 참가자 정보를 한 번에 조회합니다.
		List<GameParticipant> gameDetailsData = gameParticipantRepository.findGameDetailsByGameIds(new HashSet<>(gameIds));

		// 4. 조회된 데이터를 최종 DTO 형태로 변환합니다.
		List<CombinationDetailDto.GameDetailDto> gameDetailDtos =
			gameDetailConverter.convertToGameDetailsMulti(gameDetailsData, filter.getTeamNames(), champions);

		// 최신순으로 정렬
		gameDetailDtos.sort(Comparator.comparing(CombinationDetailDto.GameDetailDto::gameDate).reversed());

		return new CombinationDetailDto(summaryDto, gameDetailDtos);
	}

}