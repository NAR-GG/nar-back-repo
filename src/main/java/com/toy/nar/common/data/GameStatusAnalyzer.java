package com.toy.nar.common.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.toy.nar.game.repository.GameParticipantRepository;
import com.toy.nar.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameStatusAnalyzer {

	private final GameParticipantRepository gameParticipantRepository;
	private final GameRepository gameRepository;

	/**
	 * 게임 상태를 분석합니다.
	 */
	public GameStatusReport analyzeGameStatus() {
		log.info("🔍 Analyzing existing game status...");

		// 불완전한 게임 조회
		List<Object[]> incompleteGames = gameParticipantRepository.findIncompleteGames();
		Set<Long> incompleteGameIds = incompleteGames.stream()
			.map(result -> (Long) result[0])
			.collect(Collectors.toSet());

		// 전체 게임 수
		long totalGames = gameRepository.count();
		long completeGames = totalGames - incompleteGameIds.size();

		GameStatusReport report = new GameStatusReport(
			totalGames,
			completeGames,
			incompleteGameIds.size(),
			incompleteGameIds
		);

		log.info("📊 Game Status Analysis:");
		log.info("   📈 Total games: {}", report.getTotalGames());
		log.info("   ✅ Complete games: {}", report.getCompleteGames());
		log.info("   ❌ Incomplete games: {}", report.getIncompleteGames());

		return report;
	}

	public record GameStatusReport(
		long totalGames,
		long completeGames,
		long incompleteGames,
		Set<Long> incompleteGameIds
	) {
		public long getTotalGames() { return totalGames; }
		public long getCompleteGames() { return completeGames; }
		public long getIncompleteGames() { return incompleteGames; }
		public Set<Long> getIncompleteGameIds() { return incompleteGameIds; }
	}
}