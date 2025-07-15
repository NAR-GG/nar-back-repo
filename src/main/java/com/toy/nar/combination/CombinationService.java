package com.toy.nar.combination;

import com.toy.nar.combination.dto.CombinationStatDto;
import com.toy.nar.game.entity.GameParticipant;
import com.toy.nar.game.repository.GameParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.toy.nar.combination.dto.CombinationFilterDto;
import com.toy.nar.common.NameNormalizer;

import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CombinationService {

	private final GameParticipantRepository gameParticipantRepository;
	private final CombinationQueryRepository combinationQueryRepository;
	private final EntityManager entityManager;

	public List<CombinationStatDto> findTopCombinations(List<String> championNames, CombinationFilterDto filter) {
		if (championNames == null || championNames.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> normalizedChampionNames = championNames.stream()
			.map(NameNormalizer::normalizeChampionName)
			.collect(Collectors.toList());

		// 1. 해당 챔피언이 포함된 게임 ID 조회
		List<Long> gameIds = combinationQueryRepository.findGameIdsByCriteria(normalizedChampionNames, filter);

		log.info("🔍 Found {} game IDs for champions: {}", gameIds.size(), normalizedChampionNames);

		if (gameIds.isEmpty()) {
			log.warn("⚠️ No games found for champions: {}", normalizedChampionNames);
			return Collections.emptyList();
		}

		return findCombinationsContainingChampionsWithJPQL(gameIds, normalizedChampionNames);
	}

	private List<CombinationStatDto> findCombinationsContainingChampionsWithJPQL(List<Long> gameIds, List<String> championNames) {
		String jpql = """
        SELECT gp FROM GameParticipant gp
        JOIN FETCH gp.game g
        JOIN FETCH gp.champion c
        JOIN FETCH gp.team t
        WHERE g.id IN (:gameIds)
        """;

		List<GameParticipant> participants = entityManager.createQuery(jpql, GameParticipant.class)
			.setParameter("gameIds", gameIds)
			.getResultList();

		log.info("📊 Found {} participants in {} games", participants.size(), gameIds.size());

		// 게임별, 팀별 그룹화
		Map<Long, Map<String, List<GameParticipant>>> gameTeamGroups = participants.stream()
			.collect(Collectors.groupingBy(
				gp -> gp.getGame().getId(),
				Collectors.groupingBy(gp -> gp.getTeam().getName())
			));

		// 조합 빈도 계산
		Map<List<String>, Long> combinationCounts = new HashMap<>();
		int processedGames = 0;
		int validTeams = 0;
		int matchingTeams = 0;

		Set<String> championSet = new HashSet<>(championNames);

		for (Map.Entry<Long, Map<String, List<GameParticipant>>> gameEntry : gameTeamGroups.entrySet()) {
			processedGames++;
			Long gameId = gameEntry.getKey();
			Map<String, List<GameParticipant>> teamGroups = gameEntry.getValue();

			for (Map.Entry<String, List<GameParticipant>> teamEntry : teamGroups.entrySet()) {
				List<GameParticipant> teamParticipants = teamEntry.getValue();

				if (teamParticipants.size() == 5) {
					validTeams++;

					List<String> combination = teamParticipants.stream()
						.map(gp -> gp.getChampion().getChampionNameEn())
						.sorted()
						.collect(Collectors.toList());

					// 🔥 여러 챔피언이 모두 포함된 조합 확인
					if (containsAllChampions(combination, championSet)) {
						matchingTeams++;
						combinationCounts.merge(combination, 1L, Long::sum);

						// 첫 번째 매칭 조합 로깅
						if (matchingTeams == 1) {
							log.info("🎯 First matching combination found: {}", combination);
						}
					}
				}
			}
		}

		log.info("📈 Processed {} games, {} valid teams, {} matching teams, {} combinations",
			processedGames, validTeams, matchingTeams, combinationCounts.size());

		// 결과 반환
		return combinationCounts.entrySet().stream()
			.sorted(Map.Entry.<List<String>, Long>comparingByValue().reversed())
			.limit(10)
			.map(entry -> new CombinationStatDto(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private boolean containsAllChampions(List<String> combination, Set<String> championSet) {
		// 모든 챔피언이 조합에 포함되어 있는지 확인
		return championSet.stream()
			.allMatch(champion -> combination.contains(champion));
	}

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
