package com.toy.nar.service;

import com.toy.nar.entity.GameParticipant;
import com.toy.nar.repo.GameParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CombinationService {

	private final GameParticipantRepository gameParticipantRepository;

	/**
	 * 특정 포지션의 챔피언이 포함된 게임에서 가장 많이 나온 5챔피언 조합 리스트를 반환합니다.
	 *
	 * @param selectedPosition 사용자가 선택한 포지션 (예: "JUNGLE", "MID", "TOP", "ADC", "SUPPORT")
	 * @param limit 반환할 조합의 최대 개수
	 * @return 챔피언 조합 리스트와 해당 조합의 등장 횟수를 담은 Map 리스트
	 */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> getMostFrequentCombinationsByPosition(String selectedPosition, int limit) {
		log.info("Calculating most frequent combinations for position: {}", selectedPosition);

		// 1. 선택된 포지션의 챔피언이 포함된 게임 참여자 기록 조회
		List<GameParticipant> participantsInSelectedPosition = gameParticipantRepository.findByPosition(selectedPosition.toUpperCase());

		if (participantsInSelectedPosition.isEmpty()) {
			log.warn("No game participants found for position: {}", selectedPosition);
			return Collections.emptyList();
		}

		// 2. 해당 게임들의 고유한 Game ID를 추출
		Set<Long> gameIds = participantsInSelectedPosition.stream()
			.map(gp -> gp.getGame().getId()) // Game 엔티티의 ID 사용
			.collect(Collectors.toSet());

		// 3. 추출된 Game ID들을 사용하여 해당 게임들의 모든 GameParticipant 기록을 한 번에 조회
		// N+1 문제를 방지하고, 필요한 데이터만 로드합니다.
		List<GameParticipant> allParticipantsInRelevantGames = gameParticipantRepository.findByGameIds(gameIds);

		// 4. Game ID를 기준으로 그룹화하여 Map<Long, List<GameParticipant>> 생성
		// Map의 키를 Game 객체 대신 Game의 ID(Long)로 변경
		Map<Long, List<GameParticipant>> gamesMap = allParticipantsInRelevantGames.stream()
			.collect(Collectors.groupingBy(gp -> gp.getGame().getId())); // <<< Game 객체 대신 Game ID 사용

		Map<List<String>, Long> combinationCounts = new HashMap<>();

		// 5. 선택된 포지션의 각 게임 참여자 기록을 기반으로 조합을 만들고 카운트
		for (GameParticipant p : participantsInSelectedPosition) {
			Long gameId = p.getGame().getId(); // 현재 GameParticipant의 Game ID
			List<GameParticipant> allParticipantsInThisGame = gamesMap.get(gameId); // Game ID로 조회

			if (allParticipantsInThisGame == null || allParticipantsInThisGame.size() < 5) {
				// 5인 조합이 완성되지 않았거나, 데이터 누락 게임은 스킵
				continue;
			}

			List<String> combination = allParticipantsInThisGame.stream()
				.map(gp -> gp.getChampion().getChampionNameEn())
				.sorted()
				.collect(Collectors.toList());

			combinationCounts.merge(combination, 1L, Long::sum);
		}

		// 6. 카운트가 높은 순서대로 정렬하여 반환
		return combinationCounts.entrySet().stream()
			.sorted(Map.Entry.<List<String>, Long>comparingByValue().reversed())
			.limit(limit)
			.map(entry -> {
				Map<String, Object> result = new HashMap<>();
				result.put("champions", entry.getKey());
				result.put("count", entry.getValue());
				return result;
			})
			.collect(Collectors.toList());
	}
}